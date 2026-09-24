package io.github.yoyocw.aichatkit.ai.host.ruoyi.origin;

import io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication.RuoyiHostAuthenticationAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import lombok.RequiredArgsConstructor;

/** 仅明确部署在官方未改造的 native-login 链；自定义委托兑换环境不能使用本适配。 */
@RequiredArgsConstructor
public final class RuoyiHostOriginAdapter implements AiOriginContextPort {
    /** 复用正面普通 Bearer 和当前 principal/Redis/活动用户核验。 */
    private final RuoyiHostAuthenticationAdapter authentication;

    /**
     * @param appId 执行流程确定的实际应用，应用授权仍由独立授权端口核验
     * @return 此受限原生普通登录模式无机器委托来源；不因缺少某个标记直接认定普通身份
     * @throws AiIdentityException 应用缺失或普通登录核验未通过
     */
    @Override
    public AiCallerOrigin capture(String appId) {
        if (appId == null || appId.trim().isEmpty()) { throw new AiIdentityException(AiIdentityError.CONFIGURATION); }
        authentication.captureCurrent();
        return null;
    }
}
