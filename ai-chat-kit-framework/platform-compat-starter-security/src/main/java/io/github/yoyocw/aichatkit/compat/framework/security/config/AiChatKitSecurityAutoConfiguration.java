package io.github.yoyocw.aichatkit.compat.framework.security.config;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.ServiceApiKeyCommonApi;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission.PermissionCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.security.core.context.TransmittableThreadLocalSecurityContextHolderStrategy;
import io.github.yoyocw.aichatkit.compat.framework.security.core.filter.McpServiceApiEndpointRegistry;
import io.github.yoyocw.aichatkit.compat.framework.security.core.filter.ServiceApiKeyAuthenticationFilter;
import io.github.yoyocw.aichatkit.compat.framework.security.core.filter.TokenAuthenticationFilter;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.McpServiceJwtCodec;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.McpUserSessionValidator;
import io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2InspectionCredentialProvider;
import io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient;
import org.springframework.beans.factory.ObjectProvider;
import io.github.yoyocw.aichatkit.compat.framework.security.core.handler.AccessDeniedHandlerImpl;
import io.github.yoyocw.aichatkit.compat.framework.security.core.handler.AuthenticationEntryPointImpl;
import io.github.yoyocw.aichatkit.compat.framework.security.core.service.SecurityFrameworkService;
import io.github.yoyocw.aichatkit.compat.framework.security.core.service.SecurityFrameworkServiceImpl;
import io.github.yoyocw.aichatkit.compat.framework.web.core.handler.GlobalExceptionHandler;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.Resource;

/**
 * Spring Security 自动配置类，主要用于相关组件的配置
 *
 * 注意，不能和 {@link AiChatKitWebSecurityConfigurerAdapter} 用一个，原因是会导致初始化报错。
 * 参见 https://stackoverflow.com/questions/53847050/spring-boot-delegatebuilder-cannot-be-null-on-autowiring-authenticationmanager 文档。
 *
 * @author kelecc
 */
@AutoConfiguration
@AutoConfigureOrder(-1) // 目的：先于 Spring Security 自动配置，避免一键改包后，org.* 基础包无法生效
@EnableConfigurationProperties({SecurityProperties.class, McpServiceJwtProperties.class, McpSessionInspectionProperties.class})
public class AiChatKitSecurityAutoConfiguration {

    @Resource
    private SecurityProperties securityProperties;

    /**
     * 认证失败处理类 Bean
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new AuthenticationEntryPointImpl();
    }

    /**
     * 权限不够处理器 Bean
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new AccessDeniedHandlerImpl();
    }

    /**
     * Spring Security 加密器
     * 考虑到安全性，这里采用 BCryptPasswordEncoder 加密器
     *
     * @see <a href="http://stackabuse.com/password-encoding-with-spring-security/">Password Encoding with Spring Security</a>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(securityProperties.getPasswordEncoderLength());
    }

    /**
     * Token 认证过滤器 Bean
     */
    @Bean
    public TokenAuthenticationFilter authenticationTokenFilter(GlobalExceptionHandler globalExceptionHandler,
                                                               OAuth2TokenCommonApi oauth2TokenApi) {
        return new TokenAuthenticationFilter(securityProperties, globalExceptionHandler, oauth2TokenApi);
    }

    /**
     * 创建当前微服务不可变的 MCP 注解端点注册表。
     *
     * @param handlerMapping Spring MVC Controller 路由注册表
     * @return 已完成启动安全校验的 MCP 端点注册表
     */
    @Bean
    public McpServiceApiEndpointRegistry mcpServiceApiEndpointRegistry(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new McpServiceApiEndpointRegistry(handlerMapping);
    }

    /**
     * 创建 MCP 固定密钥与短期 JWT 认证过滤器。
     *
     * <p>过滤器始终注册，固定密钥查库认证，JWT 本地验签，并统一检查代码注解授权。</p>
     *
     * @param jwtCodec MCP 短期服务 JWT 本地编解码器
     * @param serviceApiKeyApi 固定密钥数据库认证 API
     * @param sessionValidator 用户 JWT 登录会话有效性检查器
     * @param endpointRegistry 当前微服务注解端点注册表
     * @return 固定服务 API Key 认证过滤器
     */
    @Bean
    public ServiceApiKeyAuthenticationFilter serviceApiKeyAuthenticationFilter(
            McpServiceJwtCodec jwtCodec, ServiceApiKeyCommonApi serviceApiKeyApi, McpUserSessionValidator sessionValidator,
            McpServiceApiEndpointRegistry endpointRegistry) {
        return new ServiceApiKeyAuthenticationFilter(serviceApiKeyApi, securityProperties, jwtCodec,
                sessionValidator, endpointRegistry);
    }

    /**
     * 默认从宿主秘密配置供给服务访问令牌；自定义同名供应器优先，不自动签发或刷新。
     * @param properties 宿主配置，不接受请求 Header 或参数作为供应来源
     * @return 按次读取当前配置的供应器
     * @throws IllegalStateException 默认供应器缺少令牌或格式非法，固定错误不携带原配置
     */
    @Bean("mcpSessionInspectionCredentialProvider")
    @ConditionalOnMissingBean(name = "mcpSessionInspectionCredentialProvider")
    @ConditionalOnProperty(prefix = "aichatkit.security.mcp-session-inspection", name = "enabled", havingValue = "true")
    public OAuth2InspectionCredentialProvider mcpSessionInspectionCredentialProvider(McpSessionInspectionProperties properties) {
        String token = properties.getConsumerAccessToken();
        if (token == null || token.isEmpty() || token.length() > 4096) {
            throw new IllegalStateException("MCP 会话核验服务凭据配置无效");
        }
        // 原始令牌必须为单个值，Bearer 前缀、空白和控制字符均不接受。
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i)) || Character.isISOControl(token.charAt(i))) {
                throw new IllegalStateException("MCP 会话核验服务凭据配置无效");
            }
        }
        return properties::getConsumerAccessToken;
    }

    /**
     * 注册业务验签方的受限会话检查器。
     * @param properties 默认关闭的新核验配置
     * @param credentials 宿主自定义或默认配置型服务消费者凭据 Bean
     * @return 每次查询当前会话状态的检查器
     */
    @Bean
    public McpUserSessionValidator mcpUserSessionValidator(McpSessionInspectionProperties properties,
            @Qualifier("mcpSessionInspectionCredentialProvider") ObjectProvider<OAuth2InspectionCredentialProvider> credentials) {
        // 未启用的宿主仅保留固定密钥能力；用户 JWT 核验不能回退旧 RPC。
        if (!properties.isEnabled()) {
            return new McpUserSessionValidator();
        }
        return new McpUserSessionValidator(new OAuth2SessionInspectionClient(properties.getBaseUrl(),
                properties.getConsumerTenantId(), credentials.getIfAvailable()));
    }

    /**
     * 创建基于 Java 标准 RSA 实现的 MCP 短期服务 JWT 编解码器。
     *
     * @param properties MCP JWT 密钥、时效和受众配置
     * @return 签发方和验签方共用的无远程依赖编解码器
     */
    @Bean
    public McpServiceJwtCodec mcpServiceJwtCodec(McpServiceJwtProperties properties) {
        return new McpServiceJwtCodec(properties);
    }

    @Bean("ss") // 使用 Spring Security 的缩写，方便使用
    public SecurityFrameworkService securityFrameworkService(PermissionCommonApi permissionApi) {
        return new SecurityFrameworkServiceImpl(permissionApi);
    }

    /**
     * 声明调用 {@link SecurityContextHolder#setStrategyName(String)} 方法，
     * 设置使用 {@link TransmittableThreadLocalSecurityContextHolderStrategy} 作为 Security 的上下文策略
     */
    @Bean
    public MethodInvokingFactoryBean securityContextHolderMethodInvokingFactoryBean() {
        MethodInvokingFactoryBean methodInvokingFactoryBean = new MethodInvokingFactoryBean();
        methodInvokingFactoryBean.setTargetClass(SecurityContextHolder.class);
        methodInvokingFactoryBean.setTargetMethod("setStrategyName");
        methodInvokingFactoryBean.setArguments(TransmittableThreadLocalSecurityContextHolderStrategy.class.getName());
        return methodInvokingFactoryBean;
    }

    /**
     * 默认的 URL 安全配置 Bean
     * 提供各模块通用的放行规则（Swagger、Actuator、Druid）
     */
    @Bean
    public DefaultAuthorizeRequestsCustomizer defaultAuthorizeRequestsCustomizer() {
        return new DefaultAuthorizeRequestsCustomizer();
    }

}
