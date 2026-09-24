package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/** 同进程模式装配入口；不在签名中引用旧 inspection 类型。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.platform-host", name = "mode", havingValue = "in-process")
@ConditionalOnExpression("'${ai-chat-kit.ai.engine.enabled:false}' == 'true'")
@EnableConfigurationProperties(PlatformInProcessProperties.class)
@Import(PlatformInProcessConfiguration.McpSubjects.class)
@AutoConfigureBefore(name = {
        "io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure.AiMcpV1SigningAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiPlainChatAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration"})
public class PlatformInProcessConfiguration {
    @Bean
    public PlatformLocalBeanResolver platformLocalBeanResolver(BeanFactory beans, ResourceLoader resources,
            PlatformInProcessProperties properties) {
        return new PlatformLocalBeanResolver(beans, resources.getClassLoader(), properties.getNativePackageRoot());
    }

    @Bean
    public PlatformLocalSessionSource platformLocalSessionSource(PlatformLocalBeanResolver beans,
            PlatformInProcessProperties properties, Environment environment) {
        return new PlatformLocalSessionSource(beans, properties.getPlatformTenantId(),
                environment.getProperty("ai-chat-kit.ai.starter.namespace"));
    }

    /** 同一个适配对象占据身份、会话、权限三端口。 */
    @Bean
    public PlatformInProcessAuthenticationAdapter platformInProcessAuthenticationAdapter(
            PlatformLocalSessionSource source) {
        return new PlatformInProcessAuthenticationAdapter(source);
    }

    @Bean
    public PlatformInProcessOriginAdapter platformInProcessOriginAdapter(
            PlatformInProcessAuthenticationAdapter authentication) {
        return new PlatformInProcessOriginAdapter(authentication);
    }

    /** 显式模式不能与旧来源或替换 Port 混用，也不能在无应用授权时半激活。 */
    @Bean
    public SmartInitializingSingleton platformInProcessPortVerifier(ListableBeanFactory beans,
            Environment environment, ResourceLoader resources, PlatformInProcessAuthenticationAdapter authentication,
            PlatformInProcessOriginAdapter origin, PlatformLocalSessionSource source) {
        return () -> {
            if (!source.namespace().equals(environment.getProperty("ai-chat-kit.ai.starter.namespace"))
                    || environment.getProperty("ai-chat-kit.ai.session-inspection.enabled", Boolean.class, false)
                    || environment.getProperty("ai-chat-kit.ai.mcp-jwt-v1.verification-enabled", Boolean.class, false)) {
                throw new IllegalStateException("同进程模式命名空间或认证源配置无效");
            }
            requireOnly(beans, AiInvocationContextPort.class, authentication);
            requireOnly(beans, AiHostSessionPort.class, authentication);
            requireOnly(beans, AiHostPermissionPort.class, authentication);
            requireOnly(beans, AiOriginContextPort.class, origin);
            requireOne(beans, AiInvocationAuthorizationPort.class);
            if (environment.getProperty("ai-chat-kit.ai.mcp-jwt-v1.signing-enabled", Boolean.class, false)) {
                try {
                    Class<?> subjectPort = ClassUtils.forName(
                            "io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort",
                            resources.getClassLoader());
                    requireOne(beans, subjectPort);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("同进程模式缺少 MCP 主体端口");
                }
            }
        };
    }

    /** 仅有 MCP 可选包时解析其 Subject SPI；关闭引擎时完全不链接可选类型。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort")
    @ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
    @ConditionalOnExpression("'${ai-chat-kit.ai.engine.enabled:false}' == 'true'")
    @ConditionalOnProperty(prefix = "ai-chat-kit.ai.platform-host", name = "mode", havingValue = "in-process")
    static class McpSubjects {
        @Bean
        public PlatformInProcessSubjectAdapter platformInProcessSubjectAdapter(PlatformLocalSessionSource source) {
            return new PlatformInProcessSubjectAdapter(source);
        }
    }

    private static <T> void requireOnly(ListableBeanFactory beans, Class<T> type, T expected) {
        java.util.Map<String, T> candidates = beans.getBeansOfType(type);
        if (candidates.size() != 1 || candidates.values().iterator().next() != expected) {
            throw new IllegalStateException("同进程模式 Port 装配冲突");
        }
    }

    private static <T> void requireOne(ListableBeanFactory beans, Class<T> type) {
        if (beans.getBeansOfType(type).size() != 1) {
            throw new IllegalStateException("同进程模式缺少唯一应用授权 Port");
        }
    }
}
