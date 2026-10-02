package io.github.yoyocw.aichatkit.module.ai.service.execution;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationResult;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.springframework.util.StringUtils;
import java.util.Objects;

/** Mandatory engine steps shared by both modes; business extensions cannot replace this guard. */
public final class AiInvocationGuard {
    private AiInvocationGuard() { }

    public static AiInvocationContext capture(AiInvocationContextPort identities, String actor,
                                               AiInvocationContext expected) {
        AiInvocationContext actual = identities.capture(actor);
        if (actual == null || (expected != null
                && (!Objects.equals(actual.getNamespace(), expected.getNamespace())
                || !Objects.equals(actual.getTenantId(), expected.getTenantId())
                || !Objects.equals(actual.getActorId(), expected.getActorId())))) {
            throw new IllegalStateException("AI 授权前后身份不一致");
        }
        return actual;
    }

    public static String authorize(AiInvocationAuthorizationPort authorization, AiInvocationContext context,
                                   AiApplicationConfig config, AiChatMode mode) {
        if (authorization == null) { throw new IllegalStateException("AI 执行缺少独立应用授权端口"); }
        if (config == null || !StringUtils.hasText(config.getAppId())) {
            throw new IllegalArgumentException("AI 应用配置缺失");
        }
        if (mode == AiChatMode.GROUP && StringUtils.hasText(config.getMcpId())) {
            throw new IllegalStateException("群聊工作流暂不支持 MCP 服务绑定，请使用用户工具配置");
        }
        boolean needsTool = StringUtils.hasText(config.getMcpId()) || !config.getUserAuthToolIds().isEmpty();
        AiInvocationAuthorizationResult result = authorization.authorize(new AiInvocationAuthorizationRequest(
                context, config.getAppId(), config.getMcpId(), config.getUserAuthToolIds()));
        if (result == null || (needsTool && !StringUtils.hasText(result.toolAuthorization()))
                || (!needsTool && result.hasToolCredential())) {
            throw new IllegalStateException("AI 应用或工具授权无效");
        }
        return result.toolAuthorization();
    }
}
