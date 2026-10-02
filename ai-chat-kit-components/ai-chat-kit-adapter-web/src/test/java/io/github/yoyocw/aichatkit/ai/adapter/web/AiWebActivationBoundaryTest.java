package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/** Disabled AI must leave a usable host MVC context even under broad component scanning. */
class AiWebActivationBoundaryTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(HostMvc.class)
            .withConfiguration(AutoConfigurations.of(AiWebExecutionAutoConfiguration.class, AiWebAutoConfiguration.class));

    @Test
    void propertiesWithoutEnableAnnotationDoNotCreateWebBeansOrRequireHostConfiguration() {
        runner.withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=true")
                .run(context -> {
                    assertWebAbsent(context);
                    assertThat(context).doesNotHaveBean(AiRuntimeActivation.class);
                });
    }

    @Test
    void annotatedHostWithEngineDisabledIgnoresEnabledWebAndMissingConfiguration() {
        runner.withUserConfiguration(EnabledHost.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false", "ai-chat-kit.ai.web.enabled=true")
                .run(context -> {
                    assertWebAbsent(context);
                    assertThat(context).hasSingleBean(AiRuntimeActivation.class);
                });
    }

    @Test
    void broadScanCannotCreateControllersOrAdviceWithoutEnableAnnotation() {
        runner.withUserConfiguration(BroadHostScan.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=true",
                        "ai-chat-kit.ai.starter.modes[0]=SINGLE", "ai-chat-kit.ai.starter.modes[1]=GROUP")
                .run(context -> {
                    assertWebAbsent(context);
                    assertThat(context).doesNotHaveBean(AiRuntimeActivation.class);
                });
    }

    @Test
    void broadScanWithAnnotationCannotBypassEngineDisabledThroughWebAdvice() {
        runner.withUserConfiguration(EnabledHost.class, BroadHostScan.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false", "ai-chat-kit.ai.web.enabled=true",
                        "ai-chat-kit.ai.starter.modes[0]=SINGLE", "ai-chat-kit.ai.starter.modes[1]=GROUP")
                .run(context -> {
                    assertWebAbsent(context);
                    assertThat(context).hasSingleBean(AiRuntimeActivation.class);
                });
    }

    @Test
    void broadScanWithWebDisabledDoesNotExposeRoutes() {
        runner.withUserConfiguration(BroadHostScan.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=false")
                .run(this::assertWebAbsent);
    }

    private void assertWebAbsent(AssertableWebApplicationContext context) {
        assertExecutionAbsent(context);
        assertBuiltInWebAbsent(context);
    }

    private void assertExecutionAbsent(AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed()
                .doesNotHaveBean(AiWebStreamResponseFactory.class)
                .doesNotHaveBean(io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter.class)
                .doesNotHaveBean(io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatExecutionService.class)
                .doesNotHaveBean(io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutionService.class)
                .doesNotHaveBean(io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamService.class);
    }

    private void assertBuiltInWebAbsent(AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed()
                .doesNotHaveBean(AiWebActivation.class)
                .doesNotHaveBean(AiWebIdentity.class)
                .doesNotHaveBean(AiWebSingleController.class)
                .doesNotHaveBean(AiWebGroupController.class)
                .doesNotHaveBean(AiWebErrorAdvice.class);
        // Verify that built-in routes are absent; independently activated hosts may still use low-level wrappers.
        assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().values())
                .noneMatch(handler -> AiWebSingleController.class.isAssignableFrom(handler.getBeanType())
                        || AiWebGroupController.class.isAssignableFrom(handler.getBeanType()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class HostMvc { }

    @Configuration(proxyBeanMethods = false)
    @EnableAiChatKit
    static class EnabledHost { }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = {"io.github.yoyocw.aichatkit.ai.adapter.web", "io.github.yoyocw.aichatkit.ai.engine.autoconfigure",
            "io.github.yoyocw.aichatkit.ai.starter.autoconfigure"},
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test(\\$.*)?"))
    static class BroadHostScan { }
}
