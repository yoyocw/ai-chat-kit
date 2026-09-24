package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import lombok.RequiredArgsConstructor;

/** 来源 capture 与身份 capture 返回类型不同，单独转发到同一个真实认证实现。 */
@RequiredArgsConstructor
public final class PlatformHostOriginAdapter implements AiOriginContextPort {
    /** 共用真实普通 Bearer 认证，不提供无条件 null 兜底。 */
    private final PlatformHostAuthenticationAdapter authentication;

    /** @return 当前普通凭据真实复核通过后才返回无委托来源；委托及未知身份抛出 */
    @Override
    public AiCallerOrigin capture(String appId) { return authentication.captureOrdinaryOrigin(appId); }
}
