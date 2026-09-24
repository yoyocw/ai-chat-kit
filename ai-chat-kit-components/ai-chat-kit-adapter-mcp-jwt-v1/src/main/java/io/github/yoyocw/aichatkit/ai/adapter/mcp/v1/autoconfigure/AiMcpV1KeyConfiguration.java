package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1KeyProperties;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1Properties;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiConfiguredMcpV1KeySource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 仅被已启用的签发或验签配置导入；自定义密钥源存在时不创建配置密钥源。 */
@EnableConfigurationProperties({AiMcpV1Properties.class, AiMcpV1KeyProperties.class})
public class AiMcpV1KeyConfiguration {
    /** @return 固定配置密钥源；不会创建、持久化或自动轮换密钥 */
    @Bean
    @ConditionalOnMissingBean(AiMcpV1KeySource.class)
    @ConditionalOnProperty(prefix = "ai-chat-kit.ai.mcp-jwt-v1.keys", name = "public-key")
    public AiMcpV1KeySource aiConfiguredMcpV1KeySource(AiMcpV1Properties properties, AiMcpV1KeyProperties keys) {
        return new AiConfiguredMcpV1KeySource(properties.getIssuer(), properties.getAudience(), keys);
    }
}
