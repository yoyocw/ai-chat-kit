package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** 使用真实Spring事务协调算法，底层仅记录生命周期，不模拟数据库回滚。 */
final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    private final RecordingTransactionState state = new RecordingTransactionState();
    int begins;
    int commits;
    int rollbacks;
    int rollbackOnlyMarks;

    @Override
    protected Object doGetTransaction() { return state; }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((RecordingTransactionState) transaction).active;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        begins++;
        state.active = true;
        state.rollbackOnly = false;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) { commits++; }

    @Override
    protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        rollbackOnlyMarks++;
        state.rollbackOnly = true;
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        state.active = false;
        state.rollbackOnly = false;
    }
}
