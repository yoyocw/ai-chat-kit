package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiModelRuntimeAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.ai.model.bailian.autoconfigure.AiBailianModelAutoConfiguration;
import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.model.*;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;

class AiModelRuntimeReplacementTest {
    // Neutral first in this input deliberately verifies the explicit provider-before-neutral order.
    private final ApplicationContextRunner base = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiModelRuntimeAutoConfiguration.class, AiBailianModelAutoConfiguration.class));
    private final ApplicationContextRunner runner = base
            .withBean(AiRuntimeActivation.class, AiRuntimeActivation::new)
            .withPropertyValues("ai-chat-kit.ai.engine.enabled=true");

    @Test void userModelPreventsCreationOfEveryDefaultProviderResource() {
        runner.withBean(AiModelClient.class, CustomModel::new)
                .withPropertyValues("ai-chat-kit.ai.bailian.read-timeout-seconds=73").run(context -> {
            assertThat(context).hasSingleBean(AiModelClient.class);
            assertThat(context).doesNotHaveBean(BailianClient.class);
            assertThat(context).doesNotHaveBean(BailianProperties.class);
            assertThat(context).doesNotHaveBean(BailianGroupOutputParser.class);
            assertThat(context.getBean(AiExecutionPolicy.class).getStaleGenerationSeconds()).isEqualTo(240);
            assertMemory(context.getBean(AiConversationMemoryService.class), 12, 6000, 2000, 3, 20);
        });
    }

    @Test void existingProviderBeanIsReusedAndConfiguredBudgetsPrecedeNeutralFallback() {
        BailianClient transport = new BailianClient(new BailianProperties());
        runner.withBean(BailianClient.class, () -> transport)
                .withPropertyValues("ai-chat-kit.ai.bailian.read-timeout-seconds=73",
                        "ai-chat-kit.ai.bailian.history-recent-message-count=2",
                        "ai-chat-kit.ai.bailian.history-max-tokens=901",
                        "ai-chat-kit.ai.bailian.history-summary-max-tokens=301",
                        "ai-chat-kit.ai.bailian.history-ascii-chars-per-token=4",
                        "ai-chat-kit.ai.bailian.history-token-safety-percent=25").run(context -> {
            assertThat(context).hasSingleBean(BailianClient.class).hasSingleBean(AiModelClient.class);
            assertThat(context.getBean(BailianClient.class)).isSameAs(transport);
            assertThat(context.getBean(AiExecutionPolicy.class).getStaleGenerationSeconds()).isEqualTo(103);
            assertMemory(context.getBean(AiConversationMemoryService.class), 2, 901, 301, 4, 25);
        });
    }

    @Test void hostExecutionAndMemoryPoliciesAreNeverReplaced() {
        AiExecutionPolicy policy = new AiExecutionPolicy(17);
        AiConversationMemoryService memory = new AiConversationMemoryService(1, 2, 3, 4, 5);
        runner.withBean(AiExecutionPolicy.class, () -> policy)
                .withBean(AiConversationMemoryService.class, () -> memory).run(context -> {
            assertThat(context).hasSingleBean(AiExecutionPolicy.class).hasSingleBean(AiConversationMemoryService.class);
            assertThat(context.getBean(AiExecutionPolicy.class)).isSameAs(policy);
            assertThat(context.getBean(AiConversationMemoryService.class)).isSameAs(memory);
            assertThat(context).hasSingleBean(AiModelClient.class);
        });
    }

    @Test void defaultProviderDoesNotRequireWebClasses() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.web", "javax.servlet")).run(context -> {
            assertThat(context).hasSingleBean(BailianClient.class).hasSingleBean(AiModelClient.class);
            assertThat(context.getBean(AiModelClient.class)).isInstanceOf(BailianModelClient.class);
            assertThat(context.getBean(AiExecutionPolicy.class).getStaleGenerationSeconds()).isEqualTo(210);
        });
    }

    @Test void providerMissingClassLeavesOnlyNeutralBudgets() {
        runner.withClassLoader(new FilteredClassLoader(BailianModelClient.class)).run(context -> {
            assertThat(context).doesNotHaveBean("bailianClient").doesNotHaveBean("bailianProperties");
            assertThat(context).doesNotHaveBean(AiModelClient.class);
            assertThat(context.getBean(AiExecutionPolicy.class).getStaleGenerationSeconds()).isEqualTo(240);
            assertMemory(context.getBean(AiConversationMemoryService.class), 12, 6000, 2000, 3, 20);
        });
    }

    @Test void missingHttpClientDoesNotCreateProviderResources() {
        runner.withClassLoader(new FilteredClassLoader("okhttp3")).run(context -> {
            assertThat(context).doesNotHaveBean("bailianClient").doesNotHaveBean("bailianProperties");
            assertThat(context).doesNotHaveBean(AiModelClient.class);
            assertThat(context).hasSingleBean(AiExecutionPolicy.class);
        });
    }

    @Test void absentMarkerOrDisabledEngineHasNoModelSideEffects() {
        assertInactive(base.withPropertyValues("ai-chat-kit.ai.engine.enabled=true"));
        assertInactive(base.withBean(AiRuntimeActivation.class, AiRuntimeActivation::new));
        assertInactive(runner.withPropertyValues("ai-chat-kit.ai.engine.enabled=false"));
    }

    private void assertInactive(ApplicationContextRunner contextRunner) {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean("bailianClient").doesNotHaveBean("bailianProperties");
            assertThat(context).doesNotHaveBean(BailianGroupOutputParser.class);
            assertThat(context).doesNotHaveBean(AiModelClient.class);
            assertThat(context).doesNotHaveBean(AiExecutionPolicy.class);
            assertThat(context).doesNotHaveBean(AiConversationMemoryService.class);
        });
    }

    private static void assertMemory(AiConversationMemoryService memory, int recent, int max, int summary, int ascii, int safety) {
        assertThat(ReflectionTestUtils.getField(memory, "recentCount")).isEqualTo(recent);
        assertThat(ReflectionTestUtils.getField(memory, "maxTokens")).isEqualTo(max);
        assertThat(ReflectionTestUtils.getField(memory, "summaryMaxTokens")).isEqualTo(summary);
        assertThat(ReflectionTestUtils.getField(memory, "asciiCharsPerToken")).isEqualTo(ascii);
        assertThat(ReflectionTestUtils.getField(memory, "safetyPercent")).isEqualTo(safety);
    }

    private static class CustomModel implements AiModelClient {
        public boolean isConfigured(AiChatMode mode, String app) { return true; }
        public void cancel(AiChatMode mode, Long id) { }
        public AiModelResult stream(AiModelRequest request, Consumer<AiModelEvent> events, BooleanSupplier probe) { return null; }
    }
}
