package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;

/** 仅在普通身份被真实复核后确认无委托来源。 */
public final class PlatformInProcessOriginAdapter implements AiOriginContextPort {
    private final PlatformInProcessAuthenticationAdapter authentication;

    public PlatformInProcessOriginAdapter(PlatformInProcessAuthenticationAdapter authentication) {
        this.authentication = authentication;
    }

    @Override
    public AiCallerOrigin capture(String appId) {
        authentication.captureOrdinaryOrigin(appId);
        return null;
    }
}
