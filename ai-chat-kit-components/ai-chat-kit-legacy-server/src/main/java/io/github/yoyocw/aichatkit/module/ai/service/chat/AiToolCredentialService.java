package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 每轮授权编排：将显式身份和应用快照交给端口，不读取宿主认证上下文。 */
@Service
@RequiredArgsConstructor
public class AiToolCredentialService {
    /** 每轮授权端口，不因缺少工具跳过应用校验。 */
    private final AiInvocationAuthorizationPort authorizationPort;

    /** @param config 本轮实际调用的数据库配置快照
     * @param context 同步宿主入口捕获的身份；端口适配器仍会复核
     * @return 可选工具委托头，仅当前调用使用，不得持久化或记录 */
    public String obtainAuthorization(AiApplicationConfig config, AiInvocationContext context) {
        // 关联编号仅用于一次调用标识，不替代现有消息 CAS 或幂等约束。
        return authorizationPort.authorize(new AiInvocationAuthorizationRequest(context,
                config.getAppId(), config.getMcpId(), config.getUserAuthToolIds())).toolAuthorization();
    }
}
