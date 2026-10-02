package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;

/**
 * 共享受限查询配置保持固定租户模式可用；普通模式开关不控制其他调用方所需的 client。
 * 属性和 client 由此唯一入口注册，不依赖扫描 ai-server 或业务控制器。
 */
@Configuration(proxyBeanMethods = false)
@Conditional(PlatformHostRuntimeCondition.class)
@EnableConfigurationProperties(AiSessionInspectionProperties.class)
@AutoConfigureBefore(name = {"io.github.yoyocw.aichatkit.module.ai.adapter.platform.host.PlatformHostConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiPlainChatAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration"})
public class PlatformInspectionAutoConfiguration {
    /**
     * 仅显式启用时创建受限 HTTPS 客户端；关闭态不解析原生登录类型。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
    @ConditionalOnExpression("'${ai-chat-kit.ai.engine.enabled:false}' == 'true'")
    @ConditionalOnProperty(prefix = "ai-chat-kit.ai.session-inspection", name = "enabled", havingValue = "true")
    static class ClientConfiguration {
        /** @return 显式开启时才连接固定认证源的客户端；缺省关闭不创建传输实例 */
        @Bean
        @ConditionalOnMissingBean(AiSessionInspectionClient.class)
        public AiSessionInspectionClient aiSessionInspectionClient(AiSessionInspectionProperties properties,
                ResourceLoader resources, Environment environment) {
            PlatformNativeLoginSource nativeLogin = PlatformNativeLoginSource.fromEnvironment(resources, environment);
            return new AiSessionInspectionClient(properties, nativeLogin.adminUserType());
        }
    }
}
