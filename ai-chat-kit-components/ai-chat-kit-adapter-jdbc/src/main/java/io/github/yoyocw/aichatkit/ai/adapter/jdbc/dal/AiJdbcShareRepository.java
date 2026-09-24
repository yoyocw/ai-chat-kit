package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcExecutionAuditAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareLease;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedConversation;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedMessage;
import java.util.Collections;
import java.util.List;

import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess.SCOPE;
import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository.parameters;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.*;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiShareConstants.*;

/** 动态公开分享的 PostgreSQL 数据访问，最终条件更新是本次访问的有效性判定点。 */
public final class AiJdbcShareRepository {
    /** 固定数据源及部署命名空间。 */
    private final AiJdbcAccess access;
    /** 创建和撤销与会话管理共用行锁顺序。 */
    private final AiJdbcChatRepository chats;

    /** 复用真实会话存储，不创建新的身份或数据范围。 */
    public AiJdbcShareRepository(AiJdbcAccess access, AiJdbcChatRepository chats) { this.access = access; this.chats = chats; }

    /** 活跃分享复用原到期时间；否则由数据库生成到期时刻并重置本轮访问统计。 */
    public AiShareLease issue(AiInvocationContext context, AiChatMode mode, Long id, int days, String candidate) {
        access.requireTransaction(); validateId(id);
        if (days < MIN_VALID_DAYS || days > MAX_VALID_DAYS) { throw new AiExecutionException(SHARE_VALID_DAYS_INVALID); }
        if (!validCode(candidate)) { throw new IllegalArgumentException("AI 分享候选码格式无效"); }
        String code = AiJdbcExecutionAuditAdapter.mode(mode); AiJdbcScope scope = access.scope(context);
        chats.lock(scope, code, id);
        List<AiShareLease> active = access.jdbc().query("SELECT share_code,share_expire_at FROM ai_runtime_conversation WHERE " + SCOPE
                + " AND mode=? AND id=? AND share_status=1 AND share_code IS NOT NULL AND share_expire_at>clock_timestamp()",
                (rs, row) -> new AiShareLease(rs.getString(1), rs.getTimestamp(2).getTime()), scope.args(code, id));
        if (!active.isEmpty()) { return active.get(0); }
        return access.jdbc().queryForObject("UPDATE ai_runtime_conversation SET share_code=?,share_status=1,"
                + "share_expire_at=clock_timestamp()+(? * INTERVAL '1 day'),share_access_count=0,share_last_access_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=? RETURNING share_code,share_expire_at",
                (rs, row) -> new AiShareLease(rs.getString(1), rs.getTimestamp(2).getTime()),
                parameters(new Object[]{candidate, days}, scope.args(code, id)));
    }

    /** 当前用户会话锁内清码并关闭；访问统计保留，旧链接随即失效。 */
    public void revoke(AiInvocationContext context, AiChatMode mode, Long id) {
        access.requireTransaction(); validateId(id); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        AiJdbcScope scope = access.scope(context); chats.lock(scope, code, id);
        access.jdbc().update("UPDATE ai_runtime_conversation SET share_code=NULL,share_status=0,share_expire_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=?", scope.args(code, id));
    }

    /**
     * 分享随机码是唯一外部能力参数；固定部署namespace下命中记录后，所有数据再按其真实归属读取。
     * 不取得创建者登录上下文，不允许访客指定任意conversationId或tenant/actor。
     */
    public AiSharedConversation readPublic(AiChatMode mode, String shareCode) {
        access.requireTransaction(); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        if (!validCode(shareCode)) { throw invalid(mode); }
        List<AiJdbcShareOwner> owners = access.jdbc().query("SELECT id,namespace,tenant_id,actor_id,title FROM ai_runtime_conversation"
                + " WHERE namespace=? AND mode=? AND share_code=? AND share_status=1 AND share_expire_at>clock_timestamp() AND deleted=false",
                (rs, row) -> new AiJdbcShareOwner(rs.getLong("id"), rs.getString("namespace"), rs.getString("tenant_id"),
                        rs.getString("actor_id"), rs.getString("title")), access.namespace(), code, shareCode);
        if (owners.size() != 1) { throw invalid(mode); }
        AiJdbcShareOwner owner = owners.get(0);
        List<AiSharedMessage> messages = publicMessages(owner, mode);
        List<AiGroupMemberSnapshot> members = mode == AiChatMode.GROUP ? access.jdbc().query("SELECT agent_code,agent_name,agent_role FROM ai_runtime_member WHERE " + SCOPE
                + " AND mode='group' AND conversation_id=? ORDER BY sort_order,id", (rs, row) -> AiJdbcMemberSnapshots.read(rs),
                owner.args(owner.conversationId)) : Collections.emptyList();
        List<String> codes = mode == AiChatMode.GROUP ? AiJdbcMemberSnapshots.codes(members) : Collections.emptyList();
        AiSharedConversation result = new AiSharedConversation(owner.title, codes, messages, members);
        // clock_timestamp而非事务开始时刻：组装期间过期、撤销、重建或删除都必须拒绝已组装结果。
        int accepted = access.jdbc().update("UPDATE ai_runtime_conversation SET share_access_count=share_access_count+1,share_last_access_at=clock_timestamp() WHERE "
                + SCOPE + " AND mode=? AND id=? AND share_code=? AND share_status=1 AND share_expire_at>clock_timestamp()",
                owner.args(code, owner.conversationId, shareCode));
        if (accepted != 1) { throw invalid(mode); }
        return result;
    }

    /** 只选择已完成的展示字段，不读取请求号、错误、模型扩展数据或用户身份作为公开响应。 */
    private List<AiSharedMessage> publicMessages(AiJdbcShareOwner owner, AiChatMode mode) {
        boolean group = mode == AiChatMode.GROUP;
        return access.jdbc().query("SELECT role,content,speaker_code,speaker_name,round_no,created_at FROM ai_runtime_message WHERE "
                + SCOPE + " AND mode=? AND conversation_id=? AND status=1 AND role IN ('user','assistant') ORDER BY id",
                (rs, row) -> new AiSharedMessage(rs.getString("role"), rs.getString("content"),
                        group ? rs.getString("speaker_code") : null, group ? rs.getString("speaker_name") : null,
                        group ? (Integer) rs.getObject("round_no") : null, rs.getTimestamp("created_at").getTime()),
                owner.args(AiJdbcExecutionAuditAdapter.mode(mode), owner.conversationId));
    }

    /** 模糊化公开错误，不能用于探测记录是否存在、被撤销或属于其他部署。 */
    private AiExecutionException invalid(AiChatMode mode) {
        return new AiExecutionException(mode == AiChatMode.SINGLE ? CHAT_SHARE_NOT_EXISTS : GROUP_SHARE_NOT_EXISTS);
    }

    /** 保持旧公开接口的32位小写hex码格式。 */
    private boolean validCode(String value) { return value != null && value.matches("[0-9a-f]{32}"); }
    /** 无效编号不能放宽创建或撤销范围。 */
    private void validateId(Long id) { if (id == null || id <= 0) { throw new IllegalArgumentException("AI 会话编号无效"); } }
}
