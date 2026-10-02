package io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.*;

/** AI 自有单聊存储一次实现准备、状态和完成能力，宿主无需逐一实现内部存储端口。 */
public final class AiJdbcSingleChatAdapter implements AiSingleChatPreparePort, AiSingleChatStatePort, AiSingleChatCompletionPort {
    /** 同一数据库的事务边界。 */
    private final AiJdbcAccess access;
    /** 带显式身份条件的参数化 SQL。 */
    private final AiJdbcChatRepository repository;

    /** 绑定同一存储访问及聊天数据访问。 */
    public AiJdbcSingleChatAdapter(AiJdbcAccess access, AiJdbcChatRepository repository) {
        this.access = access; this.repository = repository;
    }

    /** @return 是否全链使用指定访问对象，供独立状态事务装配核对同源性 */
    public boolean usesAccess(AiJdbcAccess expected) { return access == expected && repository.usesAccess(expected); }

    /** 占位准备不得独立提交，来源或业务准备失败时由发送事务整体回滚。 */
    @Override
    public AiSingleChatPreparedTurn prepare(AiSingleChatPrepareCommand command) { return repository.prepare(command); }

    /** 完成消息 CAS 与模型会话保存共用真实事务；停止获胜时不保存会话。 */
    @Override
    public boolean complete(AiSingleChatCompletionCommand command) {
        if (command == null || command.getConversationId() == null || command.getMessageId() == null
                || command.getAppId() == null || command.getAppId().trim().isEmpty()) {
            throw new IllegalArgumentException("AI 完成提交参数无效");
        }
        return Boolean.TRUE.equals(access.executor().required(() -> {
            // 与发送准备统一会话→消息锁序，避免超时清理与完成保存会话互相等待。
            repository.lock(access.scope(command.getContext()), "single", command.getConversationId());
            boolean changed = repository.terminal(command.getContext(), "single", command.getMessageId(), command.getConversationId(),
                    1, command.getContent(), null, command.getRequestId(), command.getResponseData());
            if (changed && command.getSessionId() != null && !command.getSessionId().trim().isEmpty()) {
                repository.saveSession(command.getContext(), "single", command.getConversationId(), command.getMessageId(), command.getAppId(), command.getSessionId());
            }
            return changed;
        }));
    }

    /** 缺失和未知状态独立处理，不将异常数据当作仍在生成。 */
    @Override
    public AiSingleChatState readState(AiInvocationContext context, Long messageId) {
        Integer status = repository.status(context, "single", messageId);
        if (status == null) { return AiSingleChatState.MISSING; }
        switch (status) {
            case 0: return AiSingleChatState.GENERATING;
            case 1: return AiSingleChatState.COMPLETED;
            case 2: return AiSingleChatState.STOPPED;
            case 3: return AiSingleChatState.FAILED;
            default: throw new IllegalStateException("AI 消息状态无效");
        }
    }

    /** 加入停止入口事务，状态变化和执行审计由外层整体提交。 */
    @Override
    public AiSingleChatStopResult stop(AiInvocationContext context, Long messageId) {
        access.requireTransaction();
        if (readState(context, messageId) == AiSingleChatState.MISSING) { return AiSingleChatStopResult.NOT_FOUND; }
        return repository.terminal(context, "single", messageId, null, 2, null, null, null, null)
                ? AiSingleChatStopResult.STOPPED : AiSingleChatStopResult.NOT_GENERATING;
    }

    /** 仅生成态写入已过滤的失败说明，竞争后回读真实终态。 */
    @Override
    public AiSingleChatState fail(AiSingleChatFailureCommand command) {
        if (command == null) { throw new IllegalArgumentException("AI 失败提交参数缺失"); }
        return access.executor().required(() -> {
            repository.terminal(command.getContext(), "single", command.getMessageId(), null, 3,
                    command.getPartialContent(), command.getErrorMessage(), null, null);
            return readState(command.getContext(), command.getMessageId());
        });
    }

    /** 同归属及当前轮次校验后清理过期模型会话。 */
    @Override
    public void clearExpiredSession(AiInvocationContext context, Long conversationId, Long messageId) {
        access.executor().required(() -> {
            repository.clearSession(context, "single", conversationId, messageId); return null;
        });
    }
}
