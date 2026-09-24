package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import org.springframework.transaction.support.SmartTransactionObject;

/** 测试事务状态，无数据库连接或任何持久化资源。 */
final class RecordingTransactionState implements SmartTransactionObject {
    boolean active;
    boolean rollbackOnly;

    @Override
    public boolean isRollbackOnly() { return rollbackOnly; }

    @Override
    public void flush() { }
}
