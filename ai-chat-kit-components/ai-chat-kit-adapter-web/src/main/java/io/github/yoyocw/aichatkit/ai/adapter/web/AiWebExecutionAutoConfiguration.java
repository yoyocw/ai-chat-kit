package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiSingleChatExecutor;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutor;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamExecutor;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamService;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Optional MVC transport for explicitly activated engines, including hosts with their own controllers.
 * The web.enabled switch controls built-in routes; these low-level wrappers require only engine activation.
 */
@AutoConfiguration(afterName = {
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration"},
        before = AiWebAutoConfiguration.class)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
public class AiWebExecutionAutoConfiguration {
    /** SSE writer lives only in the optional Web adapter and remains replaceable. */
    @Bean
    @ConditionalOnMissingBean(AiChatStreamEventWriter.class)
    public AiChatStreamEventWriter aiChatStreamEventWriter() { return new AiChatStreamEventWriter(); }

    @Bean
    @ConditionalOnMissingBean(AiWebStreamResponseFactory.class)
    public AiWebStreamResponseFactory aiWebStreamResponseFactory(AiChatStreamEventWriter writer) {
        return new AiWebStreamResponseFactory(writer);
    }

    /** Legacy MVC facades retain their Java names in the optional Web artifact. */
    @Bean
    @ConditionalOnMissingBean(AiChatExecutionService.class)
    @ConditionalOnBean(AiSingleChatExecutor.class)
    public AiChatExecutionService aiChatExecutionService(AiSingleChatExecutor executor, AiWebStreamResponseFactory responses) {
        return new AiChatExecutionService(executor, responses);
    }

    @Bean
    @ConditionalOnMissingBean(AiGroupChatExecutionService.class)
    @ConditionalOnBean(AiGroupChatExecutor.class)
    public AiGroupChatExecutionService aiGroupChatExecutionService(AiGroupChatExecutor executor, AiWebStreamResponseFactory responses) {
        return new AiGroupChatExecutionService(executor, responses);
    }

    @Bean
    @ConditionalOnMissingBean(AiGroupChatStreamService.class)
    @ConditionalOnBean(AiGroupChatStreamExecutor.class)
    public AiGroupChatStreamService aiGroupChatStreamService(AiGroupChatStreamExecutor executor, AiWebStreamResponseFactory responses) {
        return new AiGroupChatStreamService(executor, responses);
    }

}
