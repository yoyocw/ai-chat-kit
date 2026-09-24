package io.github.yoyocw.aichatkit.module.ai.contract.storage;

/** 单聊持久化状态，缺失记录独立表达。 */
public enum AiSingleChatState {
    /** 记录不存在或不属于当前用户。 */
    MISSING,
    /** 仍在生成。 */
    GENERATING,
    /** 已完成。 */
    COMPLETED,
    /** 已停止。 */
    STOPPED,
    /** 已失败。 */
    FAILED
}
