package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1Properties;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1AuthorizationService;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** 显式签发端，默认关闭；必须在引擎普通聊天适配器检测授权能力之前装配。 */
@AutoConfiguration(beforeName = {
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiPlainChatAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration"})
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai", name = "engine.enabled", havingValue = "true")
@Conditional(AiMcpV1AuthorizationEnabledCondition.class)
@Import(AiMcpV1KeyConfiguration.class)
public class AiMcpV1SigningAutoConfiguration {
    /** @return 唯一真实授权实现；缺少任何 SPI 或策略时拒绝启动，不装配 permissive 默认实现 */
    @Bean
    public AiMcpV1AuthorizationService aiMcpV1AuthorizationService(Environment environment,
            AiMcpV1Properties properties, ListableBeanFactory factory) {
        return new AiMcpV1AuthorizationService(environment.getRequiredProperty("ai-chat-kit.ai.starter.namespace"),
                properties, AiMcpV1Beans.unique(factory, AiInvocationContextPort.class),
                AiMcpV1Beans.unique(factory, AiHostSessionPort.class),
                AiMcpV1Beans.unique(factory, AiHostPermissionPort.class),
                () -> AiMcpV1Beans.unique(factory, AiMcpV1SubjectPort.class),
                () -> AiMcpV1Beans.unique(factory, AiMcpV1KeySource.class));
    }

    /** @return 装配完成后拒绝多个授权实现，不能因 Primary 或旧适配器同时存在而静默选错 */
    @Bean
    public SmartInitializingSingleton aiMcpV1AuthorizationUniqueness(ListableBeanFactory factory) {
        return () -> AiMcpV1Beans.unique(factory, AiInvocationAuthorizationPort.class);
    }
}
