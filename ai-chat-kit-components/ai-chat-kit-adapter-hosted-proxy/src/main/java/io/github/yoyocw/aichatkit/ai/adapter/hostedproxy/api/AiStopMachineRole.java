package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

/** 固定认证角色；调用方不能通过自报 scope 改变其用途。 */
public enum AiStopMachineRole {
    /** 发起停止的真实原业务机器。 */
    ORIGINAL,
    /** 独立授权消费机器，不能作为原业务来源。 */
    CONSUMER
}
