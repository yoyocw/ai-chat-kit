package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiModelRuntimeAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;

class AiModelRuntimeReplacementTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiModelRuntimeAutoConfiguration.class))
            .withBean(AiRuntimeActivation.class, AiRuntimeActivation::new)
            .withPropertyValues("ai-chat-kit.ai.engine.enabled=true");

    @Test void userModelPreventsCreationOfDefaultProviderResources() {
        runner.withBean(AiModelClient.class, CustomModel::new).run(context -> {
            assertThat(context).hasSingleBean(AiModelClient.class);
            assertThat(context).doesNotHaveBean(BailianClient.class);
            assertThat(context).doesNotHaveBean(BailianProperties.class);
            assertThat(context.getBean(AiExecutionPolicy.class).getStaleGenerationSeconds()).isEqualTo(240);
        });
    }
    @Test void existingProviderBeanIsReusedAndOldTimeoutConfigStillControlsExpiry() {
        runner.withBean(BailianClient.class, () -> new BailianClient(new BailianProperties()))
                .withPropertyValues("ai-chat-kit.ai.bailian.read-timeout-seconds=73").run(context -> {
                    assertThat(context).hasSingleBean(BailianClient.class).hasSingleBean(AiModelClient.class);
                    assertThat(context.getBean(AiExecutionPolicy.class).getStaleGenerationSeconds()).isEqualTo(103);
                });
    }
    private static class CustomModel implements AiModelClient {
        public boolean isConfigured(AiChatMode mode, String app) { return true; }
        public void cancel(AiChatMode mode, Long id) { }
        public AiModelResult stream(AiModelRequest request, Consumer<AiModelEvent> events, BooleanSupplier probe) { return null; }
    }
}
