package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;

/** 消费成功后本地提交参数，仅供受信内部调用，不能作为HTTP认证入口。 */
public final class AiConsumedStopCommand {
    /** 源核验后在原用户作用域捕获的真实上下文。 */
    private final AiInvocationContext context;
    /** 当前已核验原业务来源，不是消费机器身份。 */
    private final AiCallerOrigin originalCaller;
    /** 已确认成功消费的不可变票据，包括原两会话、receiver、mode、message及原始期限。 */
    private final AiStopTicket ticket;
    /** 原票据及本次所有已核验会话取最小值的截止时间，UTC Unix毫秒；只收紧不续期。 */
    private final long authorizationExpiresAtMillis;

    /** 创建不可变快照；实际身份及有效性由宿主源和协调层复核。 */
    public AiConsumedStopCommand(
            AiInvocationContext context, AiCallerOrigin originalCaller, AiStopTicket ticket,
            long authorizationExpiresAtMillis) {
        this.context = context;
        this.originalCaller = originalCaller;
        this.ticket = ticket;
        this.authorizationExpiresAtMillis = authorizationExpiresAtMillis;
    }

    /** @return 源核验后在原用户作用域捕获的真实上下文。 */
    public AiInvocationContext getContext() { return context; }
    /** @return 当前已核验原业务来源，不是消费机器身份。 */
    public AiCallerOrigin getOriginalCaller() { return originalCaller; }
    /** @return 已确认成功消费的不可变票据，包括原两会话、receiver、mode、message及原始期限。 */
    public AiStopTicket getTicket() { return ticket; }
    /** @return 已消费授权原始截止时刻，不由提交端重算 */
    public long getAuthorizationExpiresAtMillis() { return authorizationExpiresAtMillis; }
}
