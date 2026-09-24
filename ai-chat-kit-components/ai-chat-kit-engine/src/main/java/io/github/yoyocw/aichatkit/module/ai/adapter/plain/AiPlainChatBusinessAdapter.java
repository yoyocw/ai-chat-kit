package io.github.yoyocw.aichatkit.module.ai.adapter.plain;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationResult;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextStatus;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatBusinessPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.springframework.util.StringUtils;

/** 无业务上下文的默认聊天适配，应用及工具授权均交由真实宿主端口核验。 */
public final class AiPlainChatBusinessAdapter implements AiSingleChatBusinessPort {
    /** 宿主真实应用授权，不创建默认允许实现。 */
    private final AiInvocationAuthorizationPort authorization;
    /** @param authorization 必须存在的真实应用授权端口 */
    public AiPlainChatBusinessAdapter(AiInvocationAuthorizationPort authorization) {
        this.authorization = java.util.Objects.requireNonNull(authorization, "authorization");
    }

    /** 无业务请求明确标为NOT_REQUESTED；地图请求必须提供真实业务适配。 */
    @Override
    public AiBusinessSnapshot prepareBusinessContext(boolean mapEnabled, String question, AiInvocationContext context) {
        validateContext(context);
        if (mapEnabled) { throw new IllegalStateException("纯聊天默认适配不支持地图业务，请配置真实业务上下文适配"); }
        return new AiBusinessSnapshot(AiBusinessContextStatus.NOT_REQUESTED, "{}", null);
    }

    /** 没有工具仍执行应用授权；任何MCP/用户工具绑定都拒绝静默退化为空凭据。 */
    @Override
    public String getMcpAuthorization(AiApplicationConfig config, AiInvocationContext context) {
        validateContext(context);
        if (config == null || !StringUtils.hasText(config.getAppId())) { throw new IllegalArgumentException("AI 应用配置缺失"); }
        boolean needsTool = StringUtils.hasText(config.getMcpId()) || !config.getUserAuthToolIds().isEmpty();
        AiInvocationAuthorizationResult result = authorization.authorize(new AiInvocationAuthorizationRequest(context,
                config.getAppId(), config.getMcpId(), config.getUserAuthToolIds()));
        // 配置和授权结果必须一致：有工具不能缺凭据，无工具不能夹带任何凭据。
        if (result == null || (needsTool && !StringUtils.hasText(result.toolAuthorization()))
                || (!needsTool && result.hasToolCredential())) {
            throw new IllegalStateException("单聊应用或工具授权无效");
        }
        return result.toolAuthorization();
    }

    /** 只校验结构，真实认证来自引擎身份捕获与授权端口，不把本检查当作认证。 */
    private void validateContext(AiInvocationContext context) {
        if (context == null || !StringUtils.hasText(context.getNamespace()) || !StringUtils.hasText(context.getTenantId())
                || !StringUtils.hasText(context.getActorId()) || !StringUtils.hasText(context.getInvocationId())) {
            throw new IllegalArgumentException("AI 纯聊天上下文无效");
        }
    }
}
