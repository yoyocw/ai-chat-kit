package io.github.yoyocw.aichatkit.module.ai.service.chat;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;

/** 数据库停止状态成功提交后才执行模型取消，回滚不触发外部副作用。 */
public final class AiAfterCommitAction implements TransactionSynchronization {
    /** 仅捕获本轮模型请求标识的取消动作，不包含访问凭据。 */
    private final Runnable action;

    /** @param action 非空本轮提交后动作 */
    private AiAfterCommitAction(Runnable action) { this.action = Objects.requireNonNull(action, "action"); }

    /** 停止入口在修改持久化状态前确认真实事务存在，防止裸调用提前写入。 */
    public static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("AI 停止必须在真实事务入口内执行");
        }
    }

    /** @param action 仅在当前事务成功提交后执行，回滚时丢弃 */
    public static void register(Runnable action) {
        requireTransaction();
        TransactionSynchronizationManager.registerSynchronization(new AiAfterCommitAction(action));
    }

    /** Spring 确认数据库提交成功后执行本实例模型请求取消。 */
    @Override
    public void afterCommit() { action.run(); }
}
