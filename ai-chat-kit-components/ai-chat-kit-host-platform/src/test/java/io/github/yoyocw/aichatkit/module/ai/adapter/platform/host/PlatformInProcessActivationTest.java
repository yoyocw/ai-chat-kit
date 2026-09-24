package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.permission.PermissionCommonApi;
import io.github.yoyocw.aichatkit.testnative.module.system.api.oauth2.OAuth2TokenApiImpl;
import io.github.yoyocw.aichatkit.testnative.module.system.api.permission.PermissionApiImpl;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure.AiMcpV1SigningAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1AuthorizationService;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Load the complete Platform host auto-configuration set while legacy inspection types are absent. */
class PlatformInProcessActivationTest {
    private static final String NATIVE_ROOT = "ai-chat-kit.ai.platform-host.in-process.native-package-root="
            + PlatformLocalBeanResolverTest.ROOT;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformInspectionAutoConfiguration.class,
                    PlatformHostRuntimeValidationConfiguration.class, PlatformHostConfiguration.class,
                    PlatformInProcessConfiguration.class))
            .withClassLoader(new FilteredClassLoader(
                    "io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient",
                    "io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient",
                    "io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO",
                    "io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO"));

    @Test
    void bothSpringRegistrationListsContainInProcessConfiguration() throws Exception {
        Path classes = Paths.get(PlatformInProcessConfiguration.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        String name = PlatformInProcessConfiguration.class.getName();
        assertThat(new String(Files.readAllBytes(classes.resolve("META-INF/spring.factories")),
                StandardCharsets.UTF_8)).contains(name);
        assertThat(new String(Files.readAllBytes(classes.resolve(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")),
                StandardCharsets.UTF_8)).contains(name);
    }

    @Test
    void noActivationMarkerLeavesAllHostPortsAbsent() {
        runner.withUserConfiguration(LocalTargets.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.platform-host.mode=in-process")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(AiInvocationContextPort.class)
                        .doesNotHaveBean(AiHostSessionPort.class)
                        .doesNotHaveBean(AiHostPermissionPort.class)
                        .doesNotHaveBean(AiOriginContextPort.class));
    }

    @Test
    void disabledEngineLeavesAllHostPortsAbsent() {
        runner.withUserConfiguration(ActivatedLocalTargets.class, AuthorizationFixture.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false",
                        "ai-chat-kit.ai.platform-host.mode=in-process")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(AiInvocationContextPort.class)
                        .doesNotHaveBean(AiHostSessionPort.class));
    }

    @Test
    void explicitlyEnabledNativeModeRequiresPackageRoot() {
        runner.withUserConfiguration(ActivatedLocalTargets.class, AuthorizationFixture.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=platform",
                        "ai-chat-kit.ai.platform-host.mode=in-process")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void inProcessCreatesOneCohesivePortSetWithoutLegacyInspectionTypes() {
        runner.withUserConfiguration(ActivatedLocalTargets.class, AuthorizationFixture.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=platform",
                        "ai-chat-kit.ai.platform-host.mode=in-process",
                        NATIVE_ROOT,
                        "ai-chat-kit.ai.platform-host.in-process.platform-tenant-id=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(ClassUtils.isPresent(
                            "io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient",
                            context.getClassLoader())).isFalse();
                    assertThat(context.getBeansOfType(AiInvocationContextPort.class)).hasSize(1);
                    assertThat(context.getBeansOfType(AiHostSessionPort.class)).hasSize(1);
                    assertThat(context.getBeansOfType(AiHostPermissionPort.class)).hasSize(1);
                    assertThat(context.getBeansOfType(AiOriginContextPort.class)).hasSize(1);
                    assertThat(context.getBeansOfType(AiInvocationAuthorizationPort.class)).hasSize(1);
                    assertThat(context).doesNotHaveBean("aiSessionInspectionClient")
                            .doesNotHaveBean("platformHostAuthenticationAdapter");
                });
    }

    @Test
    void explicitModeRejectsAnotherIdentityPortInsteadOfChoosingPrimary() {
        runner.withUserConfiguration(ActivatedLocalTargets.class, AuthorizationFixture.class,
                        ConflictingPort.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=platform",
                        "ai-chat-kit.ai.platform-host.mode=in-process", NATIVE_ROOT)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void inProcessRejectsOrdinaryBearerAsResourceVerificationAdapter() {
        runner.withUserConfiguration(ActivatedLocalTargets.class, AuthorizationFixture.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=platform",
                        "ai-chat-kit.ai.platform-host.mode=in-process",
                        NATIVE_ROOT,
                        "ai-chat-kit.ai.mcp-jwt-v1.verification-enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void absentMcpAdapterDoesNotLinkItWhenMarkerIsMissing() {
        withoutMcp().withUserConfiguration(LocalTargets.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.platform-host.mode=in-process")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(AiInvocationContextPort.class));
    }

    @Test
    void absentMcpAdapterDoesNotLinkItWhenEngineIsOff() {
        withoutMcp().withUserConfiguration(ActivatedLocalTargets.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false",
                        "ai-chat-kit.ai.platform-host.mode=in-process")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(AiInvocationContextPort.class));
    }

    @Test
    void inProcessWithoutOptionalMcpAdapterStillCreatesOrdinaryPorts() {
        withoutMcp().withUserConfiguration(ActivatedLocalTargets.class, AuthorizationFixture.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=platform",
                        "ai-chat-kit.ai.platform-host.mode=in-process", NATIVE_ROOT)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(AiInvocationContextPort.class)
                        .hasSingleBean(AiHostSessionPort.class)
                        .hasSingleBean(AiHostPermissionPort.class)
                        .hasSingleBean(AiOriginContextPort.class));
    }

    @Test
    void neutralSigningAutoConfigurationSuppliesTheRealFifthAuthorizationPort() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformInspectionAutoConfiguration.class,
                        PlatformHostRuntimeValidationConfiguration.class, PlatformHostConfiguration.class,
                        PlatformInProcessConfiguration.class, AiMcpV1SigningAutoConfiguration.class))
                .withUserConfiguration(ActivatedLocalTargets.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=platform",
                        "ai-chat-kit.ai.platform-host.mode=in-process",
                        NATIVE_ROOT,
                        "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=plain-app",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=ai:chat:send")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(AiMcpV1AuthorizationService.class)
                        .hasSingleBean(AiInvocationAuthorizationPort.class)
                        .hasSingleBean(AiInvocationContextPort.class));
    }

    @Test
    void namespaceComesFromDeploymentConfiguration() {
        runner.withUserConfiguration(ActivatedLocalTargets.class, AuthorizationFixture.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=tenant-demo",
                        "ai-chat-kit.ai.platform-host.mode=in-process", NATIVE_ROOT)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PlatformLocalSessionSource.class);
                    assertThat(context.getBean(PlatformLocalSessionSource.class).namespace())
                            .isEqualTo("tenant-demo");
                });
    }

    private ApplicationContextRunner withoutMcp() {
        return runner.withClassLoader(new FilteredClassLoader(
                "io.github.yoyocw.aichatkit.ai.adapter.mcp.v1",
                "io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient",
                "io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient",
                "io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO",
                "io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO"));
    }

    @Configuration(proxyBeanMethods = false)
    static class LocalTargets {
        @Bean("oauth2TokenApiImpl")
        OAuth2TokenCommonApi token() { return new OAuth2TokenApiImpl(); }

        @Bean("permissionApiImpl")
        PermissionCommonApi permission() { return new PermissionApiImpl(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class ActivatedLocalTargets {
        @Bean AiRuntimeActivation aiRuntimeActivation() { return new AiRuntimeActivation(); }

        @Bean("oauth2TokenApiImpl")
        OAuth2TokenCommonApi token() { return new OAuth2TokenApiImpl(); }

        @Bean("permissionApiImpl")
        PermissionCommonApi permission() { return new PermissionApiImpl(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConflictingPort {
        @Bean AiInvocationContextPort anotherIdentityPort() { return mock(AiInvocationContextPort.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthorizationFixture {
        @Bean AiInvocationAuthorizationPort authorization() { return mock(AiInvocationAuthorizationPort.class); }
    }
}
