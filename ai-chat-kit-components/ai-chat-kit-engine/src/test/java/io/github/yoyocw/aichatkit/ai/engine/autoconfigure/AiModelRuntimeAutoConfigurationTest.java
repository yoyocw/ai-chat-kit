package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs in the engine module, whose physical test classpath excludes optional provider code. */
class AiModelRuntimeAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiModelRuntimeAutoConfiguration.class))
            .withBean(AiRuntimeActivation.class, AiRuntimeActivation::new)
            .withPropertyValues("ai-chat-kit.ai.engine.enabled=true");

    @Test void noProviderSuppliesNeutralDefaultsWithoutManufacturingModel() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AiExecutionPolicy.class).hasSingleBean(AiConversationMemoryService.class);
            assertThat(context).doesNotHaveBean(AiModelClient.class);
            assertThat(context.getBean(AiExecutionPolicy.class).getStaleGenerationSeconds()).isEqualTo(240);
            AiConversationMemoryService memory = context.getBean(AiConversationMemoryService.class);
            assertThat(ReflectionTestUtils.getField(memory, "recentCount")).isEqualTo(12);
            assertThat(ReflectionTestUtils.getField(memory, "maxTokens")).isEqualTo(6000);
            assertThat(ReflectionTestUtils.getField(memory, "summaryMaxTokens")).isEqualTo(2000);
            assertThat(ReflectionTestUtils.getField(memory, "asciiCharsPerToken")).isEqualTo(3);
            assertThat(ReflectionTestUtils.getField(memory, "safetyPercent")).isEqualTo(20);
        });
    }

    @Test void hostPoliciesHavePriorityOverNeutralDefaults() {
        AiExecutionPolicy policy = new AiExecutionPolicy(10);
        AiConversationMemoryService memory = new AiConversationMemoryService(1, 2, 3, 4, 5);
        runner.withBean(AiExecutionPolicy.class, () -> policy)
                .withBean(AiConversationMemoryService.class, () -> memory).run(context -> {
            assertThat(context.getBean(AiExecutionPolicy.class)).isSameAs(policy);
            assertThat(context.getBean(AiConversationMemoryService.class)).isSameAs(memory);
        });
    }
}
