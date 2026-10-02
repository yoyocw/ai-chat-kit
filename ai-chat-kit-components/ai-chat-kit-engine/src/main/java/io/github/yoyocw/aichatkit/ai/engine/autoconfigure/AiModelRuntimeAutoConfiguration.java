package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianGroupOutputParser;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianModelClient;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Optional model composition; replacing the SPI suppresses default provider resources. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(BailianClient.class)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@EnableConfigurationProperties
public class AiModelRuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AiExecutionPolicy aiExecutionPolicy(ObjectProvider<BailianProperties> properties) {
        BailianProperties configured = properties.getIfAvailable();
        return new AiExecutionPolicy(configured == null ? 240 : configured.getReadTimeoutSeconds() + 30L);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiConversationMemoryService aiConversationMemoryService(ObjectProvider<BailianProperties> properties) {
        BailianProperties configured = properties.getIfAvailable();
        return configured == null ? new AiConversationMemoryService(12, 6000, 2000, 3, 20)
                : new AiConversationMemoryService(configured.getHistoryRecentMessageCount(), configured.getHistoryMaxTokens(),
                configured.getHistorySummaryMaxTokens(), configured.getHistoryAsciiCharsPerToken(), configured.getHistoryTokenSafetyPercent());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(AiRuntimeActivation.class)
    @ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(AiModelClient.class)
    static class DefaultBailianConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public BailianProperties bailianProperties() { return new BailianProperties(); }

        @Bean
        @ConditionalOnMissingBean
        public BailianClient bailianClient(BailianProperties properties) { return new BailianClient(properties); }

        @Bean
        @ConditionalOnMissingBean
        public BailianGroupOutputParser bailianGroupOutputParser() { return new BailianGroupOutputParser(); }

        @Bean
        public AiModelClient aiModelClient(BailianClient client, BailianGroupOutputParser parser) {
            return new BailianModelClient(client, parser);
        }
    }
}
