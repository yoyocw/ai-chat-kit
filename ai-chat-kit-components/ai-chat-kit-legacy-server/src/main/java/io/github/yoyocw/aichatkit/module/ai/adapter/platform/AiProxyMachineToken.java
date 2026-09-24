package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 单次代理请求的临时机器凭据，只在内存中持有，不提供包含秘密的 toString。 */
@Getter
@RequiredArgsConstructor
public class AiProxyMachineToken {
    /** 原始机器访问令牌，不含 Bearer。 */
    private final String accessToken;
    /** 受限查询确认的真实租户编号。 */
    private final Long tenantId;
}
