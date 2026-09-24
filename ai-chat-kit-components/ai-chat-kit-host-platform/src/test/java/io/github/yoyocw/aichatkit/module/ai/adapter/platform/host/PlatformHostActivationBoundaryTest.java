package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Spring assembly boundaries for optional provided Platform dependencies and tenant bindings. */
class PlatformHostActivationBoundaryTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformHostRuntimeValidationConfiguration.class,
                    PlatformInspectionAutoConfiguration.class,
                    PlatformHostConfiguration.class));

    @Test
    void disabledEngineWithoutActivationDoesNotResolveProvidedPlatformRuntimeClasses() {
        runner.withClassLoader(new FilteredClassLoader("io.github.yoyocw.aichatkit.compat.framework.security",
                        "io.github.yoyocw.aichatkit.compat.framework.tenant"))
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false",
                        "ai-chat-kit.ai.platform-host.mode=ordinary-bearer")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean("platformTenantInspectionRouter")
                        .doesNotHaveBean("platformHostAuthenticationAdapter"));
    }

    @Test
    void incompleteMultiTenantListCannotSilentlyFallBackToValidLegacyClient() {
        runner.withUserConfiguration(ActivatedRuntime.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=platform",
                        "ai-chat-kit.ai.platform-host.mode=ordinary-bearer",
                        "ai-chat-kit.ai.platform-host.inspections[0].base-url=https://tenant.example",
                        "ai-chat-kit.ai.platform-host.inspections[0].consumer-access-token=multi-token",
                        "ai-chat-kit.ai.session-inspection.enabled=true",
                        "ai-chat-kit.ai.session-inspection.base-url=https://legacy.example",
                        "ai-chat-kit.ai.session-inspection.consumer-access-token=legacy-token",
                        "ai-chat-kit.ai.session-inspection.consumer-tenant-id=11",
                        "ai-chat-kit.ai.session-inspection.subject-tenant-id=11")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitlyEnabledHostFailsWhenProvidedPlatformRuntimeClassesAreMissing() {
        runner.withClassLoader(new FilteredClassLoader("io.github.yoyocw.aichatkit.compat.framework.security",
                        "io.github.yoyocw.aichatkit.compat.framework.tenant"))
                .withUserConfiguration(ActivatedRuntime.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.platform-host.mode=ordinary-bearer")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("启用平台宿主能力缺少运行时类型"));
    }

    @Configuration(proxyBeanMethods = false)
    static class ActivatedRuntime {
        @Bean
        AiRuntimeActivation aiRuntimeActivation() { return new AiRuntimeActivation(); }
    }
}
