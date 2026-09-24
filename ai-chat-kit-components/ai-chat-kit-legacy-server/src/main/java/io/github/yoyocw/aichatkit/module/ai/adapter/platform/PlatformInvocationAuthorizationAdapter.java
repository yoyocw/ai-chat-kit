package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationResult;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiDelegatedCallContext;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.McpDelegationRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.util.servlet.ServletUtils;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils;
import lombok.RequiredArgsConstructor;
import io.github.yoyocw.aichatkit.module.ai.config.AiLocalDelegationSigningProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

/** 当前林业入口的授权适配：在进入异步对话前获取凭据，显式切换签发路径。 */
@Service
@RequiredArgsConstructor
public class PlatformInvocationAuthorizationAdapter implements AiInvocationAuthorizationPort {
    /** 默认关闭的本地签发配置；开启后不能失败回退旧 RPC。 */
    private final AiLocalDelegationSigningProperties signingProperties;
    /** 条件装配的本地签发器，关闭时不要求配置私钥或受限查询。 */
    private final ObjectProvider<AiLocalDelegationSigningService> localSigning;

    /**
     * 核对本轮真实应用与工具后使用可信入口的凭据；普通入口使用 AI 本地签发。
     * @param config 可信入口生成的本轮授权范围
     * @return 成功授权结果；未使用工具时结果不携带凭据
     */
    @Override
    public AiInvocationAuthorizationResult authorize(AiInvocationAuthorizationRequest config) {
        validateIdentity(config);
        HttpServletRequest request = ServletUtils.getRequest();
        Object context = request == null ? null : request.getAttribute(AiDelegatedCallContext.class.getName());
        boolean needsTool = org.springframework.util.StringUtils.hasText(config.getMcpId())
                || !config.getToolIds().isEmpty();
        if (context instanceof AiDelegatedCallContext) {
            AiDelegatedCallContext delegated = (AiDelegatedCallContext) context;
            io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO binding = delegated.getBinding();
            if (binding == null || binding.getAppIds() == null || !binding.getAppIds().contains(config.getAppId())
                    || binding.getUserAuthToolIds() == null
                    || !binding.getUserAuthToolIds().containsAll(config.getToolIds())
                    || (org.springframework.util.StringUtils.hasText(config.getMcpId())
                        && (binding.getMcpIds() == null || !binding.getMcpIds().contains(config.getMcpId())))) {
                throw new IllegalStateException("当前应用或工具不在服务接入授权范围内");
            }
            String credential = needsTool ? delegated.getAuthorization() : null;
            if (needsTool && (credential == null || !credential.startsWith("Bearer mcp_jwt_"))) {
                throw new IllegalStateException("缺少有效工具委托");
            }
            return new AiInvocationAuthorizationResult(credential);
        }
        return new AiInvocationAuthorizationResult(needsTool ? obtainAuthorization() : null);
    }

    /** 当前认证上下文是证明来源，显式身份 DTO 不能自行授予权限。 */
    private void validateIdentity(AiInvocationAuthorizationRequest value) {
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (value == null || actor == null || actor.getId() == null || actor.getTenantId() == null
                || !"platform".equals(value.getNamespace())
                || !actor.getId().toString().equals(value.getActorId())
                || !actor.getTenantId().toString().equals(value.getTenantId())
                || !Objects.equals(actor.getTenantId(), TenantContextHolder.getTenantId())
                || !org.springframework.util.StringUtils.hasText(value.getAppId())
                || !org.springframework.util.StringUtils.hasText(value.getInvocationId())) {
            throw new IllegalStateException("本轮调用授权身份或应用无效");
        }
    }

    /**
     * 为本轮当前用户获取工具凭据；原始登录令牌仅在当前同步调用中使用。
     * @return 完整工具 Authorization 头，不得写入消息或日志
     * @throws IllegalStateException 请求缺少证明、身份不匹配或认证侧不可用
     */
    private String obtainAuthorization() {
        HttpServletRequest request = ServletUtils.getRequest();
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (request == null || user == null) {
            throw new IllegalStateException("缺少当前对话的用户委托证明");
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalStateException("缺少当前对话的用户委托证明");
        }
        McpDelegationRespDTO result;
        try {
            if (!signingProperties.isEnabled()) { throw new IllegalStateException("AI 本地委托签发未启用"); }
            result = localSigning.getObject().issue(authorization);
        } catch (RuntimeException ex) {
            // 不附加 Feign 异常，避免异常携带原始请求或响应凭据进入日志。
            throw new IllegalStateException("当前无法获取授权工具凭据，请确认登录状态后重试");
        }
        if (result == null || !Objects.equals(user.getId(), result.getUserId())
                || !Objects.equals(user.getTenantId(), result.getTenantId())
                || result.getCredential() == null || !result.getCredential().startsWith("mcp_jwt_")) {
            throw new IllegalStateException("工具委托身份与当前对话不一致");
        }
        return "Bearer " + result.getCredential();
    }
}
