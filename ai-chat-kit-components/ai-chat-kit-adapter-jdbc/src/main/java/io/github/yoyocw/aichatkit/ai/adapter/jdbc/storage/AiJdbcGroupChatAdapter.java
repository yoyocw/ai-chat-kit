package io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcGroupRepository;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.*;
import java.util.List;

/** 群聊准备和终态统一存储；多成员回复与远端会话在同一真实事务内提交。 */
public final class AiJdbcGroupChatAdapter implements AiGroupChatPreparePort, AiGroupChatStreamStatePort {
    /** 数据源和事务必须与外层执行一致。 */
    private final AiJdbcAccess access;
    /** 共用消息状态及锁序。 */
    private final AiJdbcChatRepository chats;
    /** 群聊成员关系和回复数据访问。 */
    private final AiJdbcGroupRepository groups;

    /** 绑定同一个持久化上下文。 */
    public AiJdbcGroupChatAdapter(AiJdbcAccess access, AiJdbcChatRepository chats, AiJdbcGroupRepository groups) {
        this.access = access; this.chats = chats; this.groups = groups;
    }

    /** @return 是否全链使用指定访问对象，供独立状态事务装配核对同源性 */
    public boolean usesAccess(AiJdbcAccess expected) { return access == expected && chats.usesAccess(expected) && groups.usesAccess(expected); }

    /** 由外层创建入口事务覆盖会话和成员关系。 */
    @Override
    public Long create(AiInvocationContext context, String title, List<String> codes) { return groups.create(context, title, codes); }

    /** 准备不自行提交，来源或审计失败时占位一并回滚。 */
    @Override
    public AiGroupChatPreparedTurn prepare(AiInvocationContext context, Long id, String content, String appId, long timeoutSeconds) {
        return groups.prepare(context, id, content, appId, timeoutSeconds);
    }

    /** @return 当前用户助手消息的真实状态，缺失返回 null */
    @Override
    public Integer readStatus(AiInvocationContext context, Long messageId) { return chats.status(context, "group", messageId); }

    /** 按本轮绑定清理过期模型会话，不影响随后轮次。 */
    @Override
    public void clearSession(AiInvocationContext context, Long conversationId, Long messageId) {
        access.executor().required(() -> { chats.clearSession(context, "group", conversationId, messageId); return null; });
    }

    /** 先锁会话再 CAS 占位，停止先完成时不写回复或 session。 */
    @Override
    public boolean complete(AiGroupChatCompletionCommand command) {
        if (command == null || command.getConversationId() == null || command.getMessageId() == null
                || command.getAppId() == null || command.getAppId().trim().isEmpty() || command.getReplies().isEmpty()) {
            throw new IllegalArgumentException("群聊完成参数缺失");
        }
        for (AiGroupReplyRecord reply : command.getReplies()) {
            if (reply == null || reply.getSpeakerCode() == null || reply.getSpeakerCode().trim().isEmpty()
                    || reply.getContent() == null) { throw new IllegalArgumentException("群聊回复无效"); }
        }
        return Boolean.TRUE.equals(access.executor().required(() -> {
            chats.lock(access.scope(command.getContext()), "group", command.getConversationId());
            AiGroupReplyRecord first = command.getReplies().get(0);
            if (!chats.terminal(command.getContext(), "group", command.getMessageId(), command.getConversationId(), 1,
                    first.getContent(), null, command.getRequestId(), command.getResponseData())) { return false; }
            groups.replyMetadata(command.getContext(), command.getMessageId(), first, 1, command.getRequestId());
            for (int i = 1; i < command.getReplies().size(); i++) {
                AiGroupReplyRecord reply = command.getReplies().get(i);
                Long id = chats.insert(access.scope(command.getContext()), "group", command.getConversationId(), "assistant", 1,
                        reply.getContent(), false, reply.getSpeakerCode(), reply.getSpeakerName());
                groups.replyMetadata(command.getContext(), id, reply, i + 1, command.getRequestId());
            }
            if (command.getSessionId() != null && !command.getSessionId().trim().isEmpty()) {
                chats.saveSession(command.getContext(), "group", command.getConversationId(), command.getMessageId(), command.getAppId(), command.getSessionId());
            }
            return true;
        }));
    }

    /** 数据库生成态 CAS 决定停止是否成功，由外层收口审计及取消 HTTP。 */
    @Override
    public AiSingleChatStopResult stop(AiInvocationContext context, Long messageId) {
        access.requireTransaction();
        if (readStatus(context, messageId) == null) { return AiSingleChatStopResult.NOT_FOUND; }
        return chats.terminal(context, "group", messageId, null, 2, null, null, null, null)
                ? AiSingleChatStopResult.STOPPED : AiSingleChatStopResult.NOT_GENERATING;
    }

    /** 失败不能覆盖停止或已完成状态，异常由引擎安全收口。 */
    @Override
    public boolean fail(AiInvocationContext context, Long messageId, String safeError) {
        return Boolean.TRUE.equals(access.executor().required(() ->
                chats.terminal(context, "group", messageId, null, 3, null, safeError, null, null)));
    }
}
