package io.github.yoyocw.aichatkit.ai.model.bailian.autoconfigure;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiModelRuntimeAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianGroupOutputParser;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianModelClient;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Optional provider resources and budgets; a host model suppresses every provider default. */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(AiModelRuntimeAutoConfiguration.class)
@ConditionalOnClass({BailianClient.class, BailianModelClient.class, OkHttpClient.class})
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(AiModelClient.class)
@EnableConfigurationProperties
public class AiBailianModelAutoConfiguration {
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
    @ConditionalOnMissingBean(AiModelClient.class)
    public AiModelClient aiModelClient(BailianClient client, BailianGroupOutputParser parser) {
        return new BailianModelClient(client, parser);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiExecutionPolicy aiExecutionPolicy(BailianProperties properties) {
        return new AiExecutionPolicy(properties.getReadTimeoutSeconds() + 30L);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiConversationMemoryService aiConversationMemoryService(BailianProperties properties) {
        return new AiConversationMemoryService(properties.getHistoryRecentMessageCount(), properties.getHistoryMaxTokens(),
                properties.getHistorySummaryMaxTokens(), properties.getHistoryAsciiCharsPerToken(),
                properties.getHistoryTokenSafetyPercent());
    }
}
