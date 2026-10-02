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

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.stream.Collectors;
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
        AiConversationEntity active = access.conversations().selectOne(scope.<AiConversationEntity>query(code)
                .select("share_code", "share_expire_at").eq("id", id).eq("share_status", 1).isNotNull("share_code")
                .apply("share_expire_at>clock_timestamp()"));
        if (active != null) { return new AiShareLease(active.getShareCode(), active.getShareExpireAt().getTime()); }
        AiConversationEntity issued = access.conversations().issueShare(scope, code, id, days, candidate);
        if (issued == null) { throw new IllegalStateException("AI 分享创建失败"); }
        return new AiShareLease(issued.getShareCode(), issued.getShareExpireAt().getTime());
    }

    /** 当前用户会话锁内清码并关闭；访问统计保留，旧链接随即失效。 */
    public void revoke(AiInvocationContext context, AiChatMode mode, Long id) {
        access.requireTransaction(); validateId(id); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        AiJdbcScope scope = access.scope(context); chats.lock(scope, code, id);
        access.conversations().update(null, scope.<AiConversationEntity>update(code).eq("id", id)
                .set("share_code", null).set("share_status", 0).set("share_expire_at", null).setSql("updated_at=CURRENT_TIMESTAMP"));
    }

    /**
     * 分享随机码是唯一外部能力参数；固定部署namespace下命中记录后，所有数据再按其真实归属读取。
     * 不取得创建者登录上下文，不允许访客指定任意conversationId或tenant/actor。
     */
    public AiSharedConversation readPublic(AiChatMode mode, String shareCode) {
        access.requireTransaction(); String code = AiJdbcExecutionAuditAdapter.mode(mode);
        if (!validCode(shareCode)) { throw invalid(mode); }
        List<AiConversationEntity> owners = access.conversations().selectList(new QueryWrapper<AiConversationEntity>()
                .select("id", "namespace", "tenant_id", "actor_id", "title")
                .eq("namespace", access.namespace()).eq("mode", code).eq("share_code", shareCode).eq("share_status", 1)
                .eq("deleted", false).apply("share_expire_at>clock_timestamp()"));
        if (owners.size() != 1) { throw invalid(mode); }
        AiConversationEntity owner = owners.get(0);
        AiJdbcScope scope = new AiJdbcScope(owner.getNamespace(), owner.getTenantId(), owner.getActorId());
        List<AiSharedMessage> messages = publicMessages(scope, owner.getId(), mode);
        List<AiGroupMemberSnapshot> members = mode == AiChatMode.GROUP
                ? access.members().selectList(scope.<AiMemberEntity>query("group")
                    .select("agent_code", "agent_name", "agent_role").eq("conversation_id", owner.getId()).orderByAsc("sort_order", "id"))
                    .stream().map(AiJdbcMemberSnapshots::read).collect(Collectors.toList()) : Collections.emptyList();
        List<String> codes = mode == AiChatMode.GROUP ? AiJdbcMemberSnapshots.codes(members) : Collections.emptyList();
        AiSharedConversation result = new AiSharedConversation(owner.getTitle(), codes, messages, members);
        // 使用真实时刻而非事务开始时刻：组装期间过期、撤销、重建或删除均拒绝已组装结果。
        int accepted = access.conversations().update(null, scope.<AiConversationEntity>update(code).eq("id", owner.getId())
                .eq("share_code", shareCode).eq("share_status", 1).apply("share_expire_at>clock_timestamp()")
                .setSql("share_access_count=share_access_count+1,share_last_access_at=clock_timestamp()"));
        if (accepted != 1) { throw invalid(mode); }
        return result;
    }

    /** 只选择已完成展示字段，不读取请求号、错误或模型扩展数据作为公开响应。 */
    private List<AiSharedMessage> publicMessages(AiJdbcScope scope, Long id, AiChatMode mode) {
        boolean group = mode == AiChatMode.GROUP;
        return access.messages().selectList(scope.<AiMessageEntity>query(AiJdbcExecutionAuditAdapter.mode(mode))
                .select("role", "content", "speaker_code", "speaker_name", "round_no", "created_at")
                .eq("conversation_id", id).eq("status", 1).in("role", "user", "assistant").orderByAsc("id"))
                .stream().map(row -> new AiSharedMessage(row.getRole(), row.getContent(),
                        group ? row.getSpeakerCode() : null, group ? row.getSpeakerName() : null,
                        group ? row.getRoundNo() : null, row.getCreatedAt().getTime())).collect(Collectors.toList());
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
