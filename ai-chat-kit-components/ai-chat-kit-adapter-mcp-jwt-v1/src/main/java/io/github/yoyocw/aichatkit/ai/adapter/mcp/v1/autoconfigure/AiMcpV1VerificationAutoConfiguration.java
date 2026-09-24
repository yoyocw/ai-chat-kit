package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1Properties;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1VerificationService;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** 独立资源端验签装配，默认关闭；无需引擎、私钥、Bailian 客户端或当前登录上下文。 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.mcp-jwt-v1", name = "verification-enabled", havingValue = "true")
@Import(AiMcpV1KeyConfiguration.class)
public class AiMcpV1VerificationAutoConfiguration {
    /** @return 资源端真实会话授权核验门面，不暴露 HTTP，不修改原认证过滤器 */
    @Bean
    public AiMcpV1VerificationService aiMcpV1VerificationService(Environment environment,
            AiMcpV1Properties properties, ListableBeanFactory factory) {
        return new AiMcpV1VerificationService(environment.getRequiredProperty("ai-chat-kit.ai.starter.namespace"),
                properties, AiMcpV1Beans.unique(factory, AiHostSessionPort.class),
                AiMcpV1Beans.unique(factory, AiHostPermissionPort.class),
                AiMcpV1Beans.unique(factory, AiMcpV1SubjectPort.class),
                AiMcpV1Beans.unique(factory, AiMcpV1KeySource.class));
    }
}
