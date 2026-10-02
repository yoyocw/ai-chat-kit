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

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity.*;
import java.util.stream.Collectors;

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
        return chats.createConversation(access.scope(context), "single",
                io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.DEFAULT_TITLE);
    }

    /** 全量读取当前用户的会话，最后以主键打破相同更新时间的排序平局。 */
    public List<AiConversationView> list(AiInvocationContext context, AiChatMode mode) {
        AiJdbcScope scope = access.scope(context); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        Map<Long, List<AiGroupMemberSnapshot>> members = mode == AiChatMode.GROUP ? memberSnapshots(scope) : Collections.emptyMap();
        return access.conversations().selectList(scope.<AiConversationEntity>query(code)
                .select("id", "title", "pinned", "pinned_at", "updated_at", "created_at")
                .last("ORDER BY pinned DESC,pinned_at DESC NULLS LAST,updated_at DESC,id DESC")).stream().map(row -> {
                    Timestamp pinnedAt = row.getPinnedAt();
                    List<AiGroupMemberSnapshot> snapshots = members.getOrDefault(row.getId(), Collections.emptyList());
                    List<String> codes = new ArrayList<>();
                    for (AiGroupMemberSnapshot member : snapshots) { codes.add(member.getCode()); }
                    AiMemberSnapshotStatus status = mode == AiChatMode.SINGLE ? AiMemberSnapshotStatus.NOT_APPLICABLE
                            : AiJdbcMemberSnapshots.complete(snapshots) ? AiMemberSnapshotStatus.COMPLETE : AiMemberSnapshotStatus.INCOMPLETE;
                    return new AiConversationView(row.getId(), row.getTitle(), row.getPinned(),
                            pinnedAt == null ? null : pinnedAt.getTime(), row.getUpdatedAt().getTime(),
                            codes, row.getCreatedAt().getTime(), snapshots, status);
                }).collect(Collectors.toList());
    }

    /** 查询历史前确认会话归属，实际消息查询再次排除已被并发删除的父会话。 */
    public List<AiMessageView> messages(AiInvocationContext context, AiChatMode mode, Long conversationId) {
        positive(conversationId); AiJdbcScope scope = access.scope(context); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        Long count = access.conversations().selectCount(scope.<AiConversationEntity>query(code).eq("id", conversationId));
        if (!Long.valueOf(1).equals(count)) { throw new IllegalStateException("AI 会话不存在或无权访问"); }
        return access.messages().selectList(scope.<AiMessageEntity>query(code).eq("conversation_id", conversationId)
                .select("id", "conversation_id", "role", "content", "map_enabled", "status", "request_id", "response_data",
                        "error_message", "speaker_code", "speaker_name", "round_no", "created_at")
                .exists("SELECT 1 FROM ai_runtime_conversation c WHERE c.id=ai_runtime_message.conversation_id"
                        + " AND c.namespace=ai_runtime_message.namespace AND c.tenant_id=ai_runtime_message.tenant_id"
                        + " AND c.actor_id=ai_runtime_message.actor_id AND c.mode=ai_runtime_message.mode AND c.deleted=false")
                .orderByAsc("id")).stream().map(row -> new AiMessageView(row.getId(), row.getConversationId(), row.getRole(),
                        row.getContent(), row.getMapEnabled(), row.getStatus(), row.getRequestId(), row.getResponseData(),
                        row.getErrorMessage(), row.getSpeakerCode(), row.getSpeakerName(), row.getRoundNo(), row.getCreatedAt().getTime()))
                .collect(Collectors.toList());
    }

    /** 改名只修改标题；生成中也允许，和发送共用会话行锁。 */
    public void rename(AiInvocationContext context, AiChatMode mode, Long id, String title) {
        String code = AiJdbcExecutionAuditAdapter.mode(mode);
        int max = mode == AiChatMode.SINGLE ? 100 : 30;
        if (title == null || title.trim().isEmpty() || title.length() > max) { throw new IllegalArgumentException("AI 会话标题长度无效"); }
        AiJdbcScope scope = locked(context, mode, id);
        access.conversations().update(null, scope.<AiConversationEntity>update(code).eq("id", id)
                .set("title", title.trim()).setSql("updated_at=CURRENT_TIMESTAMP"));
    }

    /** 取消置顶时显式清空时间，使用数据库时钟避免时区漂移。 */
    public void pin(AiInvocationContext context, AiChatMode mode, Long id, boolean pinned) {
        AiJdbcScope scope = locked(context, mode, id);
        access.conversations().update(null, scope.<AiConversationEntity>update(AiJdbcExecutionAuditAdapter.mode(mode))
                .eq("id", id).set("pinned", pinned).setSql(pinned ? "pinned_at=CURRENT_TIMESTAMP" : "pinned_at=NULL")
                .setSql("updated_at=CURRENT_TIMESTAMP"));
    }

    /** 允许删除生成中会话；返回的本轮模型取消动作必须等待外层事务成功提交。 */
    public List<Long> delete(AiInvocationContext context, AiChatMode mode, Long id) {
        AiJdbcScope scope = locked(context, mode, id); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        List<Long> running = access.messages().selectList(scope.<AiMessageEntity>query(code).select("id")
                .eq("conversation_id", id).eq("role", "assistant").eq("status", 0).orderByAsc("id"))
                .stream().map(AiMessageEntity::getId).collect(Collectors.toList());
        // 会话→消息→审计顺序与完成和停止兼容，旧终态不覆写为停止。
        access.messages().update(null, scope.<AiMessageEntity>update(code).eq("conversation_id", id)
                .setSql("status=CASE WHEN role='assistant' AND status=0 THEN 2 ELSE status END,updated_at=CURRENT_TIMESTAMP")
                .set("deleted", true));
        closeAudit(scope, code, id);
        access.members().update(null, scope.<AiMemberEntity>update(code).eq("conversation_id", id)
                .set("deleted", true).setSql("updated_at=CURRENT_TIMESTAMP"));
        access.conversations().update(null, scope.<AiConversationEntity>update(code).eq("id", id)
                .set("deleted", true).set("remote_session_id", null).set("turn_id", null).set("pinned", false)
                .set("pinned_at", null).set("share_code", null).set("share_status", 0).set("share_expire_at", null)
                .setSql("updated_at=CURRENT_TIMESTAMP"));
        return Collections.unmodifiableList(new ArrayList<Long>(running));
    }

    /** 成员修改在会话锁内拒绝生成中任务；空目录不提供虚构成员兜底。 */
    public void updateGroupMembers(AiInvocationContext context, Long id, List<String> codes) {
        AiJdbcScope scope = locked(context, AiChatMode.GROUP, id);
        chats.ensureIdle(scope, "group", id);
        if (catalog == null) { throw new IllegalStateException("AI 群聊真实成员目录未配置"); }
        List<AiGroupMemberSnapshot> members = catalog.resolve(context, codes);
        if (!AiJdbcMemberSnapshots.codes(members).equals(codes)) { throw new IllegalStateException("群聊目录返回成员与选择不一致"); }
        access.members().update(null, scope.<AiMemberEntity>update("group").eq("conversation_id", id)
                .set("deleted", true).setSql("updated_at=CURRENT_TIMESTAMP"));
        // 旧关系不参与部分唯一索引，允许删除后重新选择同一成员。
        AiJdbcMemberSnapshots.insert(access, scope, id, members);
        access.conversations().update(null, scope.<AiConversationEntity>update("group").eq("id", id)
                .set("remote_session_id", null).set("turn_id", null).setSql("updated_at=CURRENT_TIMESTAMP"));
    }

    /** 批量读取当前作用域真实保存值；历史缺字段留给逐会话状态判断，不阻断其他会话列表。 */
    private Map<Long, List<AiGroupMemberSnapshot>> memberSnapshots(AiJdbcScope scope) {
        Map<Long, List<AiGroupMemberSnapshot>> result = new LinkedHashMap<>();
        for (AiMemberEntity row : access.members().selectList(scope.<AiMemberEntity>query("group")
                .select("conversation_id", "agent_code", "agent_name", "agent_role").orderByAsc("conversation_id", "sort_order", "id"))) {
            result.computeIfAbsent(row.getConversationId(), ignored -> new ArrayList<>()).add(
                    new AiGroupMemberSnapshot(row.getAgentCode(), row.getAgentName(), row.getAgentRole()));
        }
        return result;
    }

    /** 删除不遗留执行中审计，状态取已持久化消息终态；保留来源、审计和已有指标供追溯。 */
    private void closeAudit(AiJdbcScope scope, String mode, Long id) {
        access.executions().update(null, scope.<AiExecutionEntity>update(mode).eq("conversation_id", id).eq("status", 0)
                .setSql("status=COALESCE((SELECT m.status FROM ai_runtime_message m"
                        + " WHERE m.id=ai_runtime_execution.message_id AND m.namespace=ai_runtime_execution.namespace"
                        + " AND m.tenant_id=ai_runtime_execution.tenant_id AND m.actor_id=ai_runtime_execution.actor_id"
                        + " AND m.mode=ai_runtime_execution.mode),2)")
                .set("error_code", "CONVERSATION_DELETED")
                .setSql("total_duration_ms=GREATEST(0,EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-created_at))*1000),updated_at=CURRENT_TIMESTAMP"));
    }

    /** 所有修改均在真实发送数据源事务内并先取得同归属会话锁。 */
    private AiJdbcScope locked(AiInvocationContext context, AiChatMode mode, Long id) {
        positive(id); access.requireTransaction(); AiJdbcScope scope = access.scope(context);
        chats.lock(scope, AiJdbcExecutionAuditAdapter.mode(mode), id); return scope;
    }

    /** 无效编号不能变为未限定查询。 */
    private static void positive(Long id) { if (id == null || id <= 0) { throw new IllegalArgumentException("AI 会话编号无效"); } }
}
