package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMemberSnapshotStatus;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcExecutionAuditAdapter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess.SCOPE;
import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository.parameters;

/** 单群聊会话与历史数据访问，保留原列表和生成期修改语义，不访问模型或宿主认证。 */
public final class AiJdbcConversationRepository {
    /** 明确的数据源及完整身份隔离参数。 */
    private final AiJdbcAccess access;
    /** 与执行准备复用同一会话行锁。 */
    private final AiJdbcChatRepository chats;
    /** 可选真实成员目录；未配置仍可读取已存快照，但不允许成员修改。 */
    private final AiGroupAgentCatalogPort catalog;

    /** 绑定现有聊天存储与可选成员目录，不构造默认成员。 */
    public AiJdbcConversationRepository(AiJdbcAccess access, AiJdbcChatRepository chats, AiGroupAgentCatalogPort catalog) {
        this.access = access; this.chats = chats; this.catalog = catalog;
    }

    /** 同外层真实事务保存默认标题和完整身份，数据库生成编号与时间，不插入消息。 */
    public Long createSingle(AiInvocationContext context) {
        access.requireTransaction();
        Long id = access.jdbc().queryForObject("INSERT INTO ai_runtime_conversation(namespace,tenant_id,actor_id,mode,title)"
                + " VALUES(?,?,?,'single',?) RETURNING id", Long.class,
                access.scope(context).args(io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.DEFAULT_TITLE));
        if (id == null) { throw new IllegalStateException("单聊创建失败"); }
        return id;
    }

    /** 全量读取当前用户的会话，最后以主键打破相同更新时间的排序平局。 */
    public List<AiConversationView> list(AiInvocationContext context, AiChatMode mode) {
        AiJdbcScope scope = access.scope(context); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        Map<Long, List<AiGroupMemberSnapshot>> members = mode == AiChatMode.GROUP ? memberSnapshots(scope) : Collections.emptyMap();
        return access.jdbc().query("SELECT id,title,pinned,pinned_at,updated_at,created_at FROM ai_runtime_conversation WHERE "
                + SCOPE + " AND mode=? ORDER BY pinned DESC,pinned_at DESC NULLS LAST,updated_at DESC,id DESC", (rs, row) -> {
                    Timestamp pinnedAt = rs.getTimestamp("pinned_at");
                    List<AiGroupMemberSnapshot> snapshots = members.getOrDefault(rs.getLong("id"), Collections.emptyList());
                    List<String> codes = new ArrayList<>();
                    for (AiGroupMemberSnapshot member : snapshots) { codes.add(member.getCode()); }
                    AiMemberSnapshotStatus status = mode == AiChatMode.SINGLE ? AiMemberSnapshotStatus.NOT_APPLICABLE
                            : AiJdbcMemberSnapshots.complete(snapshots) ? AiMemberSnapshotStatus.COMPLETE : AiMemberSnapshotStatus.INCOMPLETE;
                    return new AiConversationView(rs.getLong("id"), rs.getString("title"), rs.getBoolean("pinned"),
                            pinnedAt == null ? null : pinnedAt.getTime(), rs.getTimestamp("updated_at").getTime(),
                            codes, rs.getTimestamp("created_at").getTime(), snapshots, status);
                }, scope.args(code));
    }

    /** 查询历史前确认会话归属，实际消息查询再次排除已被并发删除的父会话。 */
    public List<AiMessageView> messages(AiInvocationContext context, AiChatMode mode, Long conversationId) {
        positive(conversationId); AiJdbcScope scope = access.scope(context); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        Integer count = access.jdbc().queryForObject("SELECT count(*) FROM ai_runtime_conversation WHERE " + SCOPE
                + " AND mode=? AND id=?", Integer.class, scope.args(code, conversationId));
        if (!Integer.valueOf(1).equals(count)) { throw new IllegalStateException("AI 会话不存在或无权访问"); }
        return access.jdbc().query("SELECT id,conversation_id,role,content,map_enabled,status,request_id,response_data,error_message,speaker_code,speaker_name,round_no,created_at"
                + " FROM ai_runtime_message WHERE " + SCOPE + " AND mode=? AND conversation_id=?"
                + " AND EXISTS (SELECT 1 FROM ai_runtime_conversation c WHERE c.id=ai_runtime_message.conversation_id"
                + " AND c.namespace=ai_runtime_message.namespace AND c.tenant_id=ai_runtime_message.tenant_id"
                + " AND c.actor_id=ai_runtime_message.actor_id AND c.mode=ai_runtime_message.mode AND c.deleted=false) ORDER BY id",
                (rs, row) -> new AiMessageView(rs.getLong("id"), rs.getLong("conversation_id"), rs.getString("role"),
                        rs.getString("content"), rs.getBoolean("map_enabled"), rs.getInt("status"), rs.getString("request_id"),
                        rs.getString("response_data"), rs.getString("error_message"), rs.getString("speaker_code"),
                        rs.getString("speaker_name"), (Integer) rs.getObject("round_no"), rs.getTimestamp("created_at").getTime()),
                scope.args(code, conversationId));
    }

    /** 改名只修改标题；生成中也允许，和发送共用会话行锁。 */
    public void rename(AiInvocationContext context, AiChatMode mode, Long id, String title) {
        String code = AiJdbcExecutionAuditAdapter.mode(mode);
        int max = mode == AiChatMode.SINGLE ? 100 : 30;
        if (title == null || title.trim().isEmpty() || title.length() > max) { throw new IllegalArgumentException("AI 会话标题长度无效"); }
        AiJdbcScope scope = locked(context, mode, id);
        access.jdbc().update("UPDATE ai_runtime_conversation SET title=?,updated_at=CURRENT_TIMESTAMP WHERE " + SCOPE
                + " AND mode=? AND id=?", parameters(new Object[]{title.trim()}, scope.args(code, id)));
    }

    /** 取消置顶时显式清空时间，使用数据库时钟避免时区漂移。 */
    public void pin(AiInvocationContext context, AiChatMode mode, Long id, boolean pinned) {
        AiJdbcScope scope = locked(context, mode, id);
        access.jdbc().update("UPDATE ai_runtime_conversation SET pinned=?,pinned_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=?", parameters(new Object[]{pinned, pinned}, scope.args(AiJdbcExecutionAuditAdapter.mode(mode), id)));
    }

    /** 允许删除生成中会话；返回的本轮模型取消动作必须等待外层事务成功提交。 */
    public List<Long> delete(AiInvocationContext context, AiChatMode mode, Long id) {
        AiJdbcScope scope = locked(context, mode, id); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        List<Long> running = access.jdbc().query("SELECT id FROM ai_runtime_message WHERE " + SCOPE
                + " AND mode=? AND conversation_id=? AND role='assistant' AND status=0 ORDER BY id",
                (rs, row) -> rs.getLong(1), scope.args(code, id));
        // 会话→消息→审计顺序与完成和停止兼容，旧终态不覆写为停止。
        access.jdbc().update("UPDATE ai_runtime_message SET status=CASE WHEN role='assistant' AND status=0 THEN 2 ELSE status END,"
                + "deleted=true,updated_at=CURRENT_TIMESTAMP WHERE " + SCOPE + " AND mode=? AND conversation_id=?", scope.args(code, id));
        closeAudit(scope, code, id);
        access.jdbc().update("UPDATE ai_runtime_member SET deleted=true,updated_at=CURRENT_TIMESTAMP WHERE " + SCOPE
                + " AND mode=? AND conversation_id=?", scope.args(code, id));
        access.jdbc().update("UPDATE ai_runtime_conversation SET deleted=true,remote_session_id=NULL,turn_id=NULL,pinned=false,pinned_at=NULL,"
                + "share_code=NULL,share_status=0,share_expire_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=?", scope.args(code, id));
        return Collections.unmodifiableList(new ArrayList<Long>(running));
    }

    /** 成员修改在会话锁内拒绝生成中任务；空目录不提供虚构成员兜底。 */
    public void updateGroupMembers(AiInvocationContext context, Long id, List<String> codes) {
        AiJdbcScope scope = locked(context, AiChatMode.GROUP, id);
        chats.ensureIdle(scope, "group", id);
        if (catalog == null) { throw new IllegalStateException("AI 群聊真实成员目录未配置"); }
        List<AiGroupMemberSnapshot> members = catalog.resolve(context, codes);
        if (!AiJdbcMemberSnapshots.codes(members).equals(codes)) { throw new IllegalStateException("群聊目录返回成员与选择不一致"); }
        access.jdbc().update("UPDATE ai_runtime_member SET deleted=true,updated_at=CURRENT_TIMESTAMP WHERE " + SCOPE
                + " AND mode='group' AND conversation_id=?", scope.args(id));
        // 旧关系逻辑删除后不参与部分唯一索引，允许删除后重新选择同一成员。
        for (int i = 0; i < members.size(); i++) {
            AiGroupMemberSnapshot member = members.get(i);
            access.jdbc().update("INSERT INTO ai_runtime_member(namespace,tenant_id,actor_id,mode,conversation_id,agent_code,agent_name,agent_role,sort_order) VALUES(?,?,?,'group',?,?,?,?,?)",
                    scope.args(id, member.getCode(), member.getName(), member.getRole(), i));
        }
        access.jdbc().update("UPDATE ai_runtime_conversation SET remote_session_id=NULL,turn_id=NULL,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode='group' AND id=?", scope.args(id));
    }

    /** 批量读取当前作用域真实保存值；历史缺字段留给逐会话状态判断，不阻断其他会话列表。 */
    private Map<Long, List<AiGroupMemberSnapshot>> memberSnapshots(AiJdbcScope scope) {
        return access.jdbc().query("SELECT conversation_id,agent_code,agent_name,agent_role FROM ai_runtime_member WHERE " + SCOPE
                + " AND mode='group' ORDER BY conversation_id,sort_order,id", rs -> {
                    Map<Long, List<AiGroupMemberSnapshot>> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.computeIfAbsent(rs.getLong("conversation_id"), ignored -> new ArrayList<>()).add(
                                new AiGroupMemberSnapshot(rs.getString("agent_code"), rs.getString("agent_name"), rs.getString("agent_role")));
                    }
                    return result;
                }, scope.args());
    }

    /** 删除不遗留执行中审计，状态取已持久化消息终态；保留来源、审计和已有指标供追溯。 */
    private void closeAudit(AiJdbcScope scope, String mode, Long id) {
        access.jdbc().update("UPDATE ai_runtime_execution SET status=COALESCE((SELECT m.status FROM ai_runtime_message m"
                + " WHERE m.id=ai_runtime_execution.message_id AND m.namespace=ai_runtime_execution.namespace"
                + " AND m.tenant_id=ai_runtime_execution.tenant_id AND m.actor_id=ai_runtime_execution.actor_id"
                + " AND m.mode=ai_runtime_execution.mode),2),error_code='CONVERSATION_DELETED',"
                + "total_duration_ms=GREATEST(0,EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-created_at))*1000),updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND conversation_id=? AND status=0", scope.args(mode, id));
    }

    /** 所有修改均在真实发送数据源事务内并先取得同归属会话锁。 */
    private AiJdbcScope locked(AiInvocationContext context, AiChatMode mode, Long id) {
        positive(id); access.requireTransaction(); AiJdbcScope scope = access.scope(context);
        chats.lock(scope, AiJdbcExecutionAuditAdapter.mode(mode), id); return scope;
    }

    /** 无效编号不能变为未限定查询。 */
    private static void positive(Long id) { if (id == null || id <= 0) { throw new IllegalArgumentException("AI 会话编号无效"); } }
}
