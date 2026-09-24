package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupReplyRecord;
import java.util.List;
import java.util.Objects;

import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess.SCOPE;
import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository.parameters;

/** 群聊成员关系及发送准备数据访问，所有查询保留命名空间、租户和用户条件。 */
public final class AiJdbcGroupRepository {
    /** 群聊与单聊使用同一真实数据源。 */
    private final AiJdbcAccess access;
    /** 复用会话行锁、历史及消息 CAS。 */
    private final AiJdbcChatRepository chats;
    /** AI 可信成员目录，不接受请求自报职责。 */
    private final AiGroupAgentCatalogPort catalog;

    /** 配置同源数据访问与已启用成员目录。 */
    public AiJdbcGroupRepository(AiJdbcAccess access, AiJdbcChatRepository chats, AiGroupAgentCatalogPort catalog) {
        this.access = access; this.chats = chats; this.catalog = catalog;
    }

    /** @return 是否全链使用指定访问对象，供独立状态事务装配核对同源性 */
    public boolean usesAccess(AiJdbcAccess expected) { return access == expected && chats.usesAccess(expected); }

    /** 同事务创建真实会话和成员；成员缺失或停用时不创建空会话。 */
    public Long create(AiInvocationContext context, String title, List<String> codes) {
        access.requireTransaction(); AiJdbcScope scope = access.scope(context);
        if (title != null && title.length() > 30) { throw new IllegalArgumentException("群聊标题过长"); }
        List<AiGroupMemberSnapshot> members = catalog.resolve(context, codes);
        if (!AiJdbcMemberSnapshots.codes(members).equals(codes)) { throw new IllegalStateException("群聊目录返回成员与选择不一致"); }
        String resolvedTitle = title == null || title.trim().isEmpty() ? AiJdbcMemberSnapshots.defaultTitle(members) : title.trim();
        if (resolvedTitle.length() > 256) { throw new IllegalArgumentException("群聊成员生成的默认标题过长"); }
        Long id = access.jdbc().queryForObject("INSERT INTO ai_runtime_conversation(namespace,tenant_id,actor_id,mode,title) VALUES(?,?,?,'group',?) RETURNING id",
                Long.class, scope.args(resolvedTitle));
        if (id == null) { throw new IllegalStateException("群聊创建失败"); }
        for (int i = 0; i < members.size(); i++) {
            AiGroupMemberSnapshot member = members.get(i);
            access.jdbc().update("INSERT INTO ai_runtime_member(namespace,tenant_id,actor_id,mode,conversation_id,agent_code,agent_name,agent_role,sort_order) VALUES(?,?,?,'group',?,?,?,?,?)",
                    scope.args(id, member.getCode(), member.getName(), member.getRole(), i));
        }
        return id;
    }

    /** 行锁下清理失联轮次、校验最新目录、推进摘要并写入本轮消息和应用绑定。 */
    public AiGroupChatPreparedTurn prepare(AiInvocationContext context, Long conversationId, String content, String appId, long timeoutSeconds) {
        access.requireTransaction(); AiJdbcScope scope = access.scope(context);
        if (conversationId == null || conversationId <= 0 || content == null || content.trim().isEmpty()
                || content.length() > 10000 || appId == null || appId.trim().isEmpty()) {
            throw new IllegalArgumentException("群聊发送参数无效");
        }
        AiJdbcConversation conversation = chats.lock(scope, "group", conversationId);
        chats.failStale(scope, "group", conversationId, timeoutSeconds);
        chats.ensureIdle(scope, "group", conversationId);
        List<AiGroupMemberSnapshot> storedMembers = access.jdbc().query("SELECT agent_code,agent_name,agent_role FROM ai_runtime_member WHERE " + SCOPE
                + " AND mode='group' AND conversation_id=? ORDER BY sort_order,id", (rs, row) -> AiJdbcMemberSnapshots.read(rs), scope.args(conversationId));
        List<String> codes = AiJdbcMemberSnapshots.codes(storedMembers);
        List<AiGroupMemberSnapshot> members = catalog.resolve(context, codes);
        String history = chats.history(scope, "group", conversation);
        String prompt = content.trim();
        String title = prompt.substring(0, prompt.offsetByCodePoints(0, Math.min(30, prompt.codePointCount(0, prompt.length()))));
        // 沿用旧链：仅尚无历史、仍为成员默认标题的会话自动命名，不覆盖用户改名。
        if (!hasText(history)) {
            access.jdbc().update("UPDATE ai_runtime_conversation SET title=? WHERE " + SCOPE
                    + " AND mode='group' AND id=? AND title=?", parameters(new Object[]{title},
                    scope.args(conversationId, AiJdbcMemberSnapshots.defaultTitle(storedMembers))));
        }
        chats.insert(scope, "group", conversationId, "user", 1, prompt, false, null, "用户");
        Long messageId = chats.insert(scope, "group", conversationId, "assistant", 0, "", false, null, null);
        boolean reset = !Objects.equals(appId, conversation.appId);
        chats.bind(scope, "group", conversationId, messageId, appId, reset);
        boolean changed = reset && (hasText(conversation.appId) || hasText(conversation.sessionId));
        return new AiGroupChatPreparedTurn(conversationId, messageId, reset ? null : conversation.sessionId, history, changed, members);
    }

    /** 只对本事务已完成的同归属回复写入发言者、顺序和请求标识。 */
    public void replyMetadata(AiInvocationContext context, Long messageId, AiGroupReplyRecord reply, int round, String requestId) {
        int updated = access.jdbc().update("UPDATE ai_runtime_message SET speaker_code=?,speaker_name=?,round_no=?,request_id=? WHERE "
                + SCOPE + " AND mode='group' AND id=? AND role='assistant' AND status=1",
                parameters(new Object[]{reply.getSpeakerCode(), reply.getSpeakerName(), round, requestId}, access.scope(context).args(messageId)));
        if (updated != 1) { throw new IllegalStateException("群聊回复归属无效"); }
    }

    /** @return 可选模型会话或应用是否有值 */
    private static boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
