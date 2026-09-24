package io.github.yoyocw.aichatkit.ai.engine.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** AI私有同步标记，随真实事务挂起/恢复；不清空或替换宿主线程资源。 */
final class AiTransactionFrame implements TransactionSynchronization {
    /** 执行器实例私有资源键。 */
    private final Object key;
    /** 被当前REQUIRES_NEW暂时覆盖的父标记，可为空。 */
    private final AiTransactionFrame previous;
    /** 当前事务实际绑定的资源持有对象。 */
    private final Object resource;
    /** 当前标记是否处于已挂起状态。 */
    private boolean suspended;

    /** @param key 私有键 @param previous 父事务标记 @param resource 当前真实资源 */
    AiTransactionFrame(Object key, AiTransactionFrame previous, Object resource) {
        this.key = key; this.previous = previous; this.resource = resource;
    }
    /** @return 当前事务尚未挂起且仍持有同一个真实资源 */
    boolean matches(Object currentResource) { return !suspended && resource == currentResource; }
    /** 挂起时保留休眠标记，用于识别同线程异库同步，不能误认为可复用宿主事务。 */
    @Override
    public void suspend() { suspended = true; }
    /** 框架恢复真正父事务后才允许再次注册AI同步动作。 */
    @Override
    public void resume() {
        if (TransactionSynchronizationManager.getResource(key) != this) {
            throw new IllegalStateException("AI事务生命周期恢复不一致");
        }
        suspended = false;
    }
    /** 仅清理本执行器的标记；内部独立事务结束后还原休眠父标记。 */
    @Override
    public void afterCompletion(int status) {
        suspended = true;
        if (TransactionSynchronizationManager.getResource(key) == this) {
            TransactionSynchronizationManager.unbindResource(key);
            if (previous != null) { TransactionSynchronizationManager.bindResource(key, previous); }
        }
    }
}

