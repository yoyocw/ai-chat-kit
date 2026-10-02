package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPrepareCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryMessage;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryResult;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;

import java.util.List;
import java.util.Objects;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity.*;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

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
            conversationId = createConversation(scope, "single", title);
        }
        AiJdbcConversation conversation = lock(scope, "single", conversationId);
        failStale(scope, "single", conversationId, command.getStaleTimeoutSeconds());
        ensureIdle(scope, "single", conversationId);
        String history = history(scope, "single", conversation);
        // 与旧单聊保持一致：独立创建的默认标题在发送时命名，用户已改名则不覆盖。
        String prompt = command.getContent().trim();
        String autoTitle = prompt.substring(0, prompt.offsetByCodePoints(0, Math.min(18, prompt.codePointCount(0, prompt.length()))));
        access.conversations().update(null, scope.<AiConversationEntity>update("single")
                .set("title", autoTitle).eq("id", conversationId)
                .eq("title", io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.DEFAULT_TITLE));
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
        AiConversationEntity row = access.conversations().selectOne(scope.<AiConversationEntity>query(mode)
                .select("id", "app_id", "remote_session_id", "memory_summary", "memory_cursor")
                .eq("id", conversationId).last("FOR UPDATE"));
        if (row == null) { throw new IllegalStateException("AI 会话不存在或无权访问"); }
        return new AiJdbcConversation(row.getId(), row.getAppId(), row.getRemoteSessionId(), row.getMemorySummary(), row.getMemoryCursor());
    }

    /** 释放超时生成占位；执行审计由同事务审计端口同步收口。 */
    public void failStale(AiJdbcScope scope, String mode, Long conversationId, long staleTimeoutSeconds) {
        access.requireTransaction();
        if (staleTimeoutSeconds <= 30 || staleTimeoutSeconds > Integer.MAX_VALUE + 30L) { throw new IllegalArgumentException("AI 超时时长无效"); }
        access.messages().update(null, scope.<AiMessageEntity>update(mode).set("status", 3)
                .set("error_message", "AI 生成超时，请重试").setSql("updated_at=CURRENT_TIMESTAMP")
                .eq("conversation_id", conversationId).eq("role", "assistant").eq("status", 0)
                .apply("created_at<CURRENT_TIMESTAMP - ({0} * INTERVAL '1 second')", staleTimeoutSeconds));
        access.executions().update(null, scope.<AiExecutionEntity>update(mode).set("status", 3).set("error_code", "STALE_TIMEOUT")
                .setSql("total_duration_ms=GREATEST(0,EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-created_at))*1000),updated_at=CURRENT_TIMESTAMP")
                .eq("conversation_id", conversationId).eq("status", 0)
                .apply("created_at<CURRENT_TIMESTAMP - ({0} * INTERVAL '1 second')", staleTimeoutSeconds));
    }

    /** 行锁下判定是否已有正在执行的轮次，数据库唯一索引提供最终并发兜底。 */
    public void ensureIdle(AiJdbcScope scope, String mode, Long conversationId) {
        Long count = access.messages().selectCount(scope.<AiMessageEntity>query(mode)
                .eq("conversation_id", conversationId).eq("role", "assistant").eq("status", 0));
        if (count == null || count > 0) { throw new IllegalStateException("当前对话正在生成回复，请稍候"); }
    }

    /** 历史只读取同作用域已完成正文，摘要游标与占位一起提交。 */
    public String history(AiJdbcScope scope, String mode, AiJdbcConversation conversation) {
        List<AiConversationMemoryMessage> messages = access.messages().selectList(scope.<AiMessageEntity>query(mode)
                .select("id", "role", "speaker_name", "content").eq("conversation_id", conversation.id)
                .eq("status", 1).ne("content", "").orderByAsc("id")).stream()
                .map(row -> new AiConversationMemoryMessage(row.getId(),
                        "user".equals(row.getRole()) ? "用户" : row.getSpeakerName(), row.getContent()))
                .collect(Collectors.toList());
        AiConversationMemoryResult result = memory.build(conversation.summary, conversation.cursor, messages);
        access.conversations().update(null, scope.<AiConversationEntity>update(mode).eq("id", conversation.id)
                .set("memory_summary", result.getSummary()).set("memory_cursor", result.getCursorMessageId())
                .setSql("updated_at=CURRENT_TIMESTAMP"));
        return result.getContext();
    }

    /** @return 真实数据库生成的消息主键；身份字段直接来自已验证作用域 */
    public long insert(AiJdbcScope scope, String mode, Long conversationId, String role, int status, String content,
                       boolean mapEnabled, String speakerCode, String speakerName) {
        access.requireTransaction();
        AiMessageEntity row = scope.initialize(new AiMessageEntity(), mode);
        row.setConversationId(conversationId); row.setRole(role); row.setStatus(status); row.setContent(content);
        row.setMapEnabled(mapEnabled); row.setSpeakerCode(speakerCode); row.setSpeakerName(speakerName);
        if (access.messages().insert(row) != 1 || row.getId() == null) { throw new IllegalStateException("AI 消息写入失败"); }
        return row.getId();
    }

    /** 绑定本轮应用和占位，应用切换时只重置远端会话。 */
    public void bind(AiJdbcScope scope, String mode, Long conversationId, Long messageId, String appId, boolean reset) {
        UpdateWrapper<AiConversationEntity> update = scope.<AiConversationEntity>update(mode).eq("id", conversationId)
                .set("app_id", appId).set("turn_id", messageId).setSql("updated_at=CURRENT_TIMESTAMP");
        if (reset) { update.set("remote_session_id", null); }
        access.conversations().update(null, update);
    }

    /** @return 当前作用域助手消息状态；缺失返回 null */
    public Integer status(AiInvocationContext context, String mode, Long messageId) {
        AiMessageEntity row = access.messages().selectOne(access.scope(context).<AiMessageEntity>query(mode)
                .select("status").eq("id", messageId).eq("role", "assistant"));
        return row == null ? null : row.getStatus();
    }

    /** CAS 收口终态；完成内容与同事务远端会话写入由上层组织。 */
    public boolean terminal(AiInvocationContext context, String mode, Long messageId, Long conversationId,
                            int status, String content, String error, String requestId, String response) {
        UpdateWrapper<AiMessageEntity> update = access.scope(context).<AiMessageEntity>update(mode)
                .eq("id", messageId).eq("role", "assistant").eq("status", 0)
                .eq(conversationId != null, "conversation_id", conversationId).set("status", status)
                .set("error_message", error).set("request_id", requestId).set("response_data", response)
                .setSql("updated_at=CURRENT_TIMESTAMP");
        if (content != null) { update.set("content", content); }
        return access.messages().update(null, update) == 1;
    }

    /** 迟到轮次无法更新新轮次的模型会话；所有归属条件显式绑定。 */
    public void saveSession(AiInvocationContext context, String mode, Long conversationId, Long messageId, String appId, String sessionId) {
        access.conversations().update(null, access.scope(context).<AiConversationEntity>update(mode)
                .eq("id", conversationId).eq("turn_id", messageId).eq("app_id", appId)
                .set("remote_session_id", sessionId).setSql("updated_at=CURRENT_TIMESTAMP"));
    }

    /** 清理会话仅允许当前绑定轮次，不能清理随后轮次的会话。 */
    public void clearSession(AiInvocationContext context, String mode, Long conversationId, Long messageId) {
        if (status(context, mode, messageId) == null) { throw new IllegalStateException("AI 消息归属无效"); }
        access.conversations().update(null, access.scope(context).<AiConversationEntity>update(mode)
                .eq("id", conversationId).eq("turn_id", messageId)
                .set("remote_session_id", null).setSql("updated_at=CURRENT_TIMESTAMP"));
    }

    /** 数据库生成主键，插入必要业务值，保留数据库默认时间和状态。 */
    public Long createConversation(AiJdbcScope scope, String mode, String title) {
        access.requireTransaction();
        AiConversationEntity row = scope.initialize(new AiConversationEntity(), mode);
        row.setTitle(title);
        if (access.conversations().insert(row) != 1 || row.getId() == null) { throw new IllegalStateException("AI 会话创建失败"); }
        return row.getId();
    }

    /** @return 可选模型绑定值是否非空 */
    private static boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
