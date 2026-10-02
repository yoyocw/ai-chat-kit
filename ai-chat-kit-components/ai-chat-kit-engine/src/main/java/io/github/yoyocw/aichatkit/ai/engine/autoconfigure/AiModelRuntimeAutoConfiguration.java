package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Neutral execution budgets, available even when no optional model adapter is installed. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
public class AiModelRuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AiExecutionPolicy aiExecutionPolicy() {
        return new AiExecutionPolicy(240);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiConversationMemoryService aiConversationMemoryService() {
        return new AiConversationMemoryService(12, 6000, 2000, 3, 20);
    }
}
