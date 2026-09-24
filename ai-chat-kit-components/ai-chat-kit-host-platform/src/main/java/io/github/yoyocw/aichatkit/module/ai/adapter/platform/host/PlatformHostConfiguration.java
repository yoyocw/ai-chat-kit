package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 仅 ai-chat-kit.ai.platform-host.mode=ordinary-bearer 时启用；缺省仍是原平台链。
 * 新接入使用逐租户 inspections；未配置列表时保留 session-inspection 固定租户兼容接线。
 * 仅接受已认证后台普通 Bearer 与当前租户上下文，不开放委托或请求指定租户，不生成来源标记。
 * 不得与 MCP v1 资源验签同时启用；资源端需独立的无当前普通登录上下文会话/权限实现。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnExpression("'${ai-chat-kit.ai.engine.enabled:false}' == 'true' && "
        + "'${ai-chat-kit.ai.platform-host.mode:}' == 'ordinary-bearer'")
@ConditionalOnClass(name = {"io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient",
        "io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser",
        "io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils",
        "io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder",
        "javax.servlet.http.HttpServletRequest"})
@EnableConfigurationProperties(PlatformHostInspectionProperties.class)
@AutoConfigureAfter(PlatformInspectionAutoConfiguration.class)
@AutoConfigureBefore(name = {"io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiPlainChatAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure.AiJdbcStorageAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration"})
public class PlatformHostConfiguration {
    /** 每个绑定创建一个消费者租户等于主体租户的受限客户端，由 Spring 关闭资源。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(PlatformTenantInspectionRouter.class)
    @Conditional(PlatformMultiTenantInspectionCondition.class)
    public PlatformTenantInspectionRouter platformTenantInspectionRouter(
            PlatformHostInspectionProperties properties) {
        return new PlatformTenantInspectionRouter(properties);
    }

    /** @return 共享真实校验的身份、会话和权限实现；错误配置必须阻止启用 */
    @Bean
    public PlatformHostAuthenticationAdapter platformHostAuthenticationAdapter(
            ObjectProvider<PlatformTenantInspectionRouter> routers,
            ObjectProvider<AiSessionInspectionClient> inspections,
            AiSessionInspectionProperties properties, Environment environment) {
        if (environment.getProperty("ai-chat-kit.ai.mcp-jwt-v1.verification-enabled", Boolean.class, false)) {
            throw new IllegalStateException("平台普通 Bearer 模式不能作为 MCP 资源验签侧的会话或权限适配");
        }
        if (!"platform".equals(environment.getProperty("ai-chat-kit.ai.starter.namespace", "platform"))) {
            throw new IllegalStateException("平台普通 Bearer 模式需要 platform 命名空间");
        }
        PlatformTenantInspectionRouter router = routers.getIfAvailable();
        if (router != null) { return new PlatformHostAuthenticationAdapter(router); }
        AiSessionInspectionClient inspection = inspections.getIfAvailable();
        if (inspection == null || !properties.isEnabled() || properties.getSubjectTenantId() == null
                || properties.getSubjectTenantId() < 0) {
            throw new IllegalStateException("平台普通 Bearer 模式需要多租户绑定或已启用的固定租户复核");
        }
        return new PlatformHostAuthenticationAdapter(inspection, properties);
    }

    /** @return 与身份共用正面凭据核验的来源适配，不与旧来源混用 */
    @Bean
    public PlatformHostOriginAdapter platformHostOriginAdapter(PlatformHostAuthenticationAdapter authentication) {
        return new PlatformHostOriginAdapter(authentication);
    }

    /**
     * 启用后四端口各自只能有本组对象；不以 Primary 隐藏旧实现或自定义实现混用。
     * @return 全部单例创建后的组装检查
     */
    @Bean
    public SmartInitializingSingleton platformHostPortVerifier(ListableBeanFactory beans,
            PlatformHostAuthenticationAdapter authentication, PlatformHostOriginAdapter origin) {
        return () -> {
            requireOnly(beans, AiInvocationContextPort.class, authentication);
            requireOnly(beans, AiHostSessionPort.class, authentication);
            requireOnly(beans, AiHostPermissionPort.class, authentication);
            requireOnly(beans, AiOriginContextPort.class, origin);
        };
    }

    /** 校验实际对象而非仅有指定类，任何多候选或替换都会使显式模式启动失败。 */
    private <T> void requireOnly(ListableBeanFactory beans, Class<T> type, T expected) {
        java.util.Map<String, T> candidates = beans.getBeansOfType(type);
        if (candidates.size() != 1 || candidates.values().iterator().next() != expected) {
            throw new IllegalStateException("平台普通 Bearer 模式不允许混用身份、会话、权限或来源适配");
        }
    }
}
