package io.github.yoyocw.aichatkit.ai.engine.transaction;

/** 明确的事务资源归属策略，不影响宿主默认事务管理器。 */
public enum AiTransactionMode {
    /** 加入同源宿主现有事务，沿用原林业本地原子性。 */
    REUSE_HOST,
    /** AI私有资源，仅接纳本执行器建立的事务生命周期。 */
    ISOLATED
}

