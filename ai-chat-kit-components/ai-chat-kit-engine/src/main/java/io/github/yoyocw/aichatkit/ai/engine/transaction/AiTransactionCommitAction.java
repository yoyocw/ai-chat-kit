package io.github.yoyocw.aichatkit.ai.engine.transaction;

import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronization;

/** 已由执行器校验并登记到选中事务的提交后动作，不在回滚时触发。 */
final class AiTransactionCommitAction implements TransactionSynchronization {
    /** 不持有认证凭据的本实例取消动作。 */
    private final Runnable action;
    /** @param action 提交后动作 */
    AiTransactionCommitAction(Runnable action) { this.action = Objects.requireNonNull(action); }
    /** 只由Spring真正提交后的同步阶段调用。 */
    @Override
    public void afterCommit() { action.run(); }
}

