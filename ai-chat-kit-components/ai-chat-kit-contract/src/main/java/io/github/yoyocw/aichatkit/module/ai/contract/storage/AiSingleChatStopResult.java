package io.github.yoyocw.aichatkit.module.ai.contract.storage;

/** 停止CAS结果，由业务层映射既有错误码。 */
public enum AiSingleChatStopResult {
    /** 本次成功停止。 */
    STOPPED,
    /** 助手消息不存在或不属于当前用户。 */
    NOT_FOUND,
    /** 已进入终态或CAS竞争失败。 */
    NOT_GENERATING
}
