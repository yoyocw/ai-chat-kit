package io.github.yoyocw.aichatkit.ai.host.ruoyi.autoconfigure;

import io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication.RuoyiHostAuthenticationAdapter;
import io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication.RuoyiHostPermissions;
import io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication.RuoyiSessionReader;
import io.github.yoyocw.aichatkit.ai.host.ruoyi.config.RuoyiHostProperties;
import io.github.yoyocw.aichatkit.ai.host.ruoyi.origin.RuoyiHostOriginAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.Map;

/**
 * 官方锁定 RuoYi-Vue 原生普通登录的显式装配；宿主依赖管理需对齐 Boot 2.7.18。
 * 只复用宿主原服务，不扫描若依包、不创建认证服务或权限后备，不修改认证过滤器。
 * 单租户域是部署隔离约束，不是原若依支持多租户的声明；不支持自定义委托改写。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.ruoyi-host", name = "enabled", havingValue = "true")
@Conditional(RuoyiHostRuntimeCondition.class)
@EnableConfigurationProperties(RuoyiHostProperties.class)
@AutoConfigureBefore(name = {"io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure.AiMcpV1SigningAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure.AiJdbcStorageAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiPlainChatAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration"})
public class RuoyiHostAutoConfiguration {
    /** @return 固定权限映射快照；没有默认全权映射 */
    @Bean
    public RuoyiHostPermissions ruoyiHostPermissions(RuoyiHostProperties properties) {
        return new RuoyiHostPermissions(properties.getPermissionMappings());
    }

    /** @return 查询原 Redis、用户和菜单权限的只读能力，缺任意真实服务拒绝启用 */
    @Bean
    public RuoyiSessionReader ruoyiSessionReader(RuoyiHostProperties properties, Environment environment,
            ListableBeanFactory beans) {
        validate(properties, environment);
        return new RuoyiSessionReader(environment.getProperty("ai-chat-kit.ai.starter.namespace"), properties.getSingleTenantId(),
                unique(beans, RedisCache.class), unique(beans, ISysUserService.class), unique(beans, SysPermissionService.class));
    }

    /** @return 三个端口共用同一套真实认证，不接受 Primary 隐藏另一身份域 */
    @Bean
    public RuoyiHostAuthenticationAdapter ruoyiHostAuthenticationAdapter(RuoyiHostProperties properties,
            Environment environment, ListableBeanFactory beans, RuoyiSessionReader sessions, RuoyiHostPermissions permissions) {
        validate(properties, environment);
        return new RuoyiHostAuthenticationAdapter(environment.getProperty("ai-chat-kit.ai.starter.namespace"),
                properties.getSingleTenantId(), environment.getProperty("token.header"), unique(beans, TokenService.class), sessions, permissions);
    }

    /** @return 仅原生普通登录合同下的正面来源核验 */
    @Bean
    public RuoyiHostOriginAdapter ruoyiHostOriginAdapter(RuoyiHostAuthenticationAdapter authentication) {
        return new RuoyiHostOriginAdapter(authentication);
    }

    /** @return 防止与林业、自定义委托或其他认证适配混用的实际对象检查 */
    @Bean
    public SmartInitializingSingleton ruoyiHostPortVerifier(ListableBeanFactory beans,
            RuoyiHostAuthenticationAdapter authentication, RuoyiHostOriginAdapter origin) {
        return () -> {
            requireOnly(beans, AiInvocationContextPort.class, authentication);
            requireOnly(beans, AiHostSessionPort.class, authentication);
            requireOnly(beans, AiHostPermissionPort.class, authentication);
            requireOnly(beans, AiOriginContextPort.class, origin);
        };
    }

    /** 配置只固定原宿主部署，不隐式开启工具资源或代理停止能力。 */
    private void validate(RuoyiHostProperties properties, Environment environment) {
        String namespace = environment.getProperty("ai-chat-kit.ai.starter.namespace");
        String header = environment.getProperty("token.header");
        if (!properties.isEnabled() || !"native-login".equals(properties.getMode()) || !identifier(namespace)
                || !identifier(properties.getSingleTenantId()) || header == null || !header.matches("[A-Za-z0-9-]{1,128}")
                || environment.getProperty("ai-chat-kit.ai.mcp-jwt-v1.verification-enabled", Boolean.class, false)
                || environment.getProperty("ai-chat-kit.ai.hosted-proxy.stop-enabled", Boolean.class, false)) {
            throw invalid();
        }
    }

    /** 部署隔离值保持原样且有界，不截断或改写标识。 */
    private boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9._-]{1,128}"); }

    /** 多候选不选择任意 Primary，宿主原服务必须唯一。 */
    private <T> T unique(ListableBeanFactory beans, Class<T> type) {
        Map<String, T> values = beans.getBeansOfType(type);
        if (values.size() != 1) { throw invalid(); }
        return values.values().iterator().next();
    }

    /** 校验最终解析对象，不能仅按 Bean 名称判断属于同一认证域。 */
    private <T> void requireOnly(ListableBeanFactory beans, Class<T> type, T expected) {
        if (unique(beans, type) != expected) { throw invalid(); }
    }

    /** @return 无宿主实现详情的明确配置失败 */
    private static AiIdentityException invalid() { return new AiIdentityException(AiIdentityError.CONFIGURATION); }
}
