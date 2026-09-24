package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPrepareCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryMessage;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryResult;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;

import java.util.List;
import java.util.Objects;

import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess.SCOPE;

/** PostgreSQL 聊天数据访问；会话归属、行锁、占位与终态 CAS 共用显式隔离条件。 */
public final class AiJdbcChatRepository {
    /** 固定数据库与事务边界。 */
    private final AiJdbcAccess access;
    /** AI 自有记忆算法。 */
    private final AiConversationMemoryService memory;

    /** 使用同数据源操作与既有记忆算法。 */
    public AiJdbcChatRepository(AiJdbcAccess access, AiConversationMemoryService memory) {
        this.access = access; this.memory = memory;
    }

    /** @return 是否全链使用指定访问对象，供独立状态事务装配核对同源性 */
    public boolean usesAccess(AiJdbcAccess expected) { return access == expected; }

    /** 同外层发送事务锁定会话、清理失联占位、更新摘要和写入本轮两条消息。 */
    public AiSingleChatPreparedTurn prepare(AiSingleChatPrepareCommand command) {
        access.requireTransaction();
        if (command == null || command.getContent() == null || command.getContent().trim().isEmpty()
                || command.getContent().length() > 10000 || command.getAppId() == null
                || command.getAppId().trim().isEmpty() || command.getStaleTimeoutSeconds() <= 30
                || command.getStaleTimeoutSeconds() > Integer.MAX_VALUE + 30L) {
            throw new IllegalArgumentException("AI 发送准备参数无效");
        }
        AiJdbcScope scope = access.scope(command.getContext());
        Long conversationId = command.getConversationId();
        if (conversationId == null) {
            String content = command.getContent().trim();
            String title = content.substring(0, content.offsetByCodePoints(0, Math.min(18, content.codePointCount(0, content.length()))));
            conversationId = access.jdbc().queryForObject("INSERT INTO ai_runtime_conversation"
                    + "(namespace,tenant_id,actor_id,mode,title) VALUES(?,?,?,'single',?) RETURNING id",
                    Long.class, scope.args(title));
        }
        AiJdbcConversation conversation = lock(scope, "single", conversationId);
        failStale(scope, "single", conversationId, command.getStaleTimeoutSeconds());
        ensureIdle(scope, "single", conversationId);
        String history = history(scope, "single", conversation);
        // 与旧单聊保持一致：独立创建的默认标题在发送时命名，用户已改名则不覆盖。
        String prompt = command.getContent().trim();
        String autoTitle = prompt.substring(0, prompt.offsetByCodePoints(0, Math.min(18, prompt.codePointCount(0, prompt.length()))));
        access.jdbc().update("UPDATE ai_runtime_conversation SET title=? WHERE " + SCOPE
                + " AND mode='single' AND id=? AND title=?", parameters(new Object[]{autoTitle},
                scope.args(conversationId, io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.DEFAULT_TITLE)));
        insert(scope, "single", conversationId, "user", 1, command.getContent().trim(), command.isMapEnabled(), null, null);
        long assistantId = insert(scope, "single", conversationId, "assistant", 0, "", command.isMapEnabled(), null, null);
        boolean reset = !Objects.equals(command.getAppId(), conversation.appId);
        bind(scope, "single", conversationId, assistantId, command.getAppId(), reset);
        boolean changed = reset && (hasText(conversation.appId) || hasText(conversation.sessionId));
        return new AiSingleChatPreparedTurn(conversationId, assistantId, reset ? null : conversation.sessionId, changed, history);
    }

    /** @return 同归属会话的行锁快照；不存在或非当前用户均拒绝 */
    public AiJdbcConversation lock(AiJdbcScope scope, String mode, Long conversationId) {
        access.requireTransaction();
        List<AiJdbcConversation> rows = access.jdbc().query("SELECT id,app_id,remote_session_id,memory_summary,memory_cursor"
                + " FROM ai_runtime_conversation WHERE " + SCOPE + " AND mode=? AND id=? FOR UPDATE",
                (rs, row) -> new AiJdbcConversation(rs.getLong("id"), rs.getString("app_id"),
                        rs.getString("remote_session_id"), rs.getString("memory_summary"), (Long) rs.getObject("memory_cursor")),
                scope.args(mode, conversationId));
        if (rows.size() != 1) { throw new IllegalStateException("AI 会话不存在或无权访问"); }
        return rows.get(0);
    }

    /** 释放超时生成占位；执行审计由同事务审计端口同步收口。 */
    public void failStale(AiJdbcScope scope, String mode, Long conversationId, long staleTimeoutSeconds) {
        access.requireTransaction();
        if (staleTimeoutSeconds <= 30 || staleTimeoutSeconds > Integer.MAX_VALUE + 30L) { throw new IllegalArgumentException("AI 超时时长无效"); }
        access.jdbc().update("UPDATE ai_runtime_message SET status=3,error_message='AI 生成超时，请重试',updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND conversation_id=? AND role='assistant' AND status=0 AND created_at<CURRENT_TIMESTAMP - (? * INTERVAL '1 second')",
                scope.args(mode, conversationId, staleTimeoutSeconds));
        access.jdbc().update("UPDATE ai_runtime_execution SET status=3,error_code='STALE_TIMEOUT',"
                + "total_duration_ms=GREATEST(0,EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-created_at))*1000),updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND conversation_id=? AND status=0 AND created_at<CURRENT_TIMESTAMP - (? * INTERVAL '1 second')",
                scope.args(mode, conversationId, staleTimeoutSeconds));
    }

    /** 行锁下判定是否已有正在执行的轮次，数据库唯一索引提供最终并发兜底。 */
    public void ensureIdle(AiJdbcScope scope, String mode, Long conversationId) {
        Integer count = access.jdbc().queryForObject("SELECT count(*) FROM ai_runtime_message WHERE " + SCOPE
                + " AND mode=? AND conversation_id=? AND role='assistant' AND status=0", Integer.class,
                scope.args(mode, conversationId));
        if (count == null || count > 0) { throw new IllegalStateException("当前对话正在生成回复，请稍候"); }
    }

    /** 历史只读取同作用域已完成正文，摘要游标与占位一起提交。 */
    public String history(AiJdbcScope scope, String mode, AiJdbcConversation conversation) {
        List<AiConversationMemoryMessage> messages = access.jdbc().query("SELECT id,role,speaker_name,content FROM ai_runtime_message WHERE "
                + SCOPE + " AND mode=? AND conversation_id=? AND status=1 AND content<>'' ORDER BY id",
                (rs, row) -> new AiConversationMemoryMessage(rs.getLong("id"),
                        "user".equals(rs.getString("role")) ? "用户" : rs.getString("speaker_name"), rs.getString("content")),
                scope.args(mode, conversation.id));
        AiConversationMemoryResult result = memory.build(conversation.summary, conversation.cursor, messages);
        access.jdbc().update("UPDATE ai_runtime_conversation SET memory_summary=?,memory_cursor=?,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=?", parameters(new Object[]{result.getSummary(), result.getCursorMessageId()}, scope.args(mode, conversation.id)));
        return result.getContext();
    }

    /** @return 真实数据库生成的消息主键；身份字段直接来自已验证作用域 */
    public long insert(AiJdbcScope scope, String mode, Long conversationId, String role, int status, String content,
                       boolean mapEnabled, String speakerCode, String speakerName) {
        access.requireTransaction();
        Long id = access.jdbc().queryForObject("INSERT INTO ai_runtime_message(namespace,tenant_id,actor_id,mode,conversation_id,role,status,content,map_enabled,speaker_code,speaker_name)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?) RETURNING id", Long.class,
                scope.args(mode, conversationId, role, status, content, mapEnabled, speakerCode, speakerName));
        if (id == null) { throw new IllegalStateException("AI 消息写入失败"); }
        return id;
    }

    /** 绑定本轮应用和占位，应用切换时只重置远端会话。 */
    public void bind(AiJdbcScope scope, String mode, Long conversationId, Long messageId, String appId, boolean reset) {
        access.jdbc().update("UPDATE ai_runtime_conversation SET app_id=?,turn_id=?,remote_session_id=CASE WHEN ? THEN NULL ELSE remote_session_id END,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=?", parameters(new Object[]{appId, messageId, reset}, scope.args(mode, conversationId)));
    }

    /** @return 当前作用域助手消息状态；缺失返回 null */
    public Integer status(AiInvocationContext context, String mode, Long messageId) {
        List<Integer> values = access.jdbc().query("SELECT status FROM ai_runtime_message WHERE " + SCOPE
                + " AND mode=? AND id=? AND role='assistant'", (rs, row) -> rs.getInt(1), access.scope(context).args(mode, messageId));
        return values.isEmpty() ? null : values.get(0);
    }

    /** CAS 收口终态；完成内容与同事务远端会话写入由上层组织。 */
    public boolean terminal(AiInvocationContext context, String mode, Long messageId, Long conversationId,
                            int status, String content, String error, String requestId, String response) {
        Object[] prefix = {status, content, error, requestId, response};
        return access.jdbc().update("UPDATE ai_runtime_message SET status=?,content=COALESCE(?,content),error_message=?,request_id=?,response_data=?,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=? AND role='assistant' AND status=0 AND (CAST(? AS bigint) IS NULL OR conversation_id=?)",
                parameters(prefix, access.scope(context).args(mode, messageId, conversationId, conversationId))) == 1;
    }

    /** 迟到轮次无法更新新轮次的模型会话；所有归属条件显式绑定。 */
    public void saveSession(AiInvocationContext context, String mode, Long conversationId, Long messageId, String appId, String sessionId) {
        access.jdbc().update("UPDATE ai_runtime_conversation SET remote_session_id=?,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=? AND turn_id=? AND app_id=?",
                parameters(new Object[]{sessionId}, access.scope(context).args(mode, conversationId, messageId, appId)));
    }

    /** 清理会话仅允许当前绑定轮次，不能清理随后轮次的会话。 */
    public void clearSession(AiInvocationContext context, String mode, Long conversationId, Long messageId) {
        if (status(context, mode, messageId) == null) { throw new IllegalStateException("AI 消息归属无效"); }
        access.jdbc().update("UPDATE ai_runtime_conversation SET remote_session_id=NULL,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND id=? AND turn_id=?", access.scope(context).args(mode, conversationId, messageId));
    }

    /** 合并 SET 与 WHERE 绑定参数，不拼接任何外部值为 SQL。 */
    public static Object[] parameters(Object[] prefix, Object[] suffix) {
        Object[] values = new Object[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, values, 0, prefix.length); System.arraycopy(suffix, 0, values, prefix.length, suffix.length);
        return values;
    }

    /** @return 可选模型绑定值是否非空 */
    private static boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
