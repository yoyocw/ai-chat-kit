package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 区分未请求与查询成功；成功允许空数据，查询失败由异常表达。 */
public enum AiBusinessContextStatus {
    NOT_REQUESTED,
    READY
}
