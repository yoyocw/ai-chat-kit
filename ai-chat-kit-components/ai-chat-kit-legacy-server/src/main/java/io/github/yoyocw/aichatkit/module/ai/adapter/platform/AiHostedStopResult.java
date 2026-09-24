package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceCallerRespDTO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 当前核验结果，不含任何原始令牌；不是 AI 原消息来源或状态校验结果。 */
@Getter
@RequiredArgsConstructor
public final class AiHostedStopResult {
    /** 当前原用户访问会话ID。 */
    private final Long userSessionId;
    /** 当前原业务机器访问会话ID，不是消费者会话。 */
    private final Long originalMachineSessionId;
    /** 当前用户ID。 */
    private final Long userId;
    /** 最新原业务调用方身份和应用允许列表。 */
    private final AiServiceCallerRespDTO originalCaller;
    /** 原用户和机器会话到期最小值；检查/消费响应同时受票据到期限制，Unix毫秒。 */
    private final long sessionExpiresAt;
}
