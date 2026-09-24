package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1AuthorizationService;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiConfiguredMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1VerificationService;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationResult;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** 普通聊天授权属于 AI 运行时，签发子开关不能绕过显式激活边界。 */
class AiMcpV1ActivationBoundaryTest {

    /** 宽扫描不能让关闭状态下的密钥配置脱离自动配置边界。 */
    @Test
    void broadScanDoesNotCreateKeySourceWithoutRuntimeMarker() {
        new ApplicationContextRunner()
                .withUserConfiguration(BroadMcpScan.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.verification-enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.keys.public-key=test-public-key")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AiMcpV1KeySource.class));
    }

    /** 即使宿主存在激活标记，引擎关闭时宽扫描也不能创建密钥源。 */
    @Test
    void broadScanDoesNotCreateKeySourceWhenEngineDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(BroadMcpScan.class, ActivatedRuntime.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.verification-enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.keys.public-key=test-public-key")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AiMcpV1KeySource.class));
    }

    /** 资源端独立验签仍须通过显式导入装配密钥源，且不依赖聊天运行时标记。 */
    @Test
    void verificationExplicitlyImportsKeyConfigurationWithoutRuntimeMarker() throws Exception {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AiMcpV1VerificationAutoConfiguration.class))
                .withUserConfiguration(HostIdentityPorts.class, VerificationSubjectPort.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.mcp-jwt-v1.verification-enabled=true",
                        "ai-chat-kit.ai.starter.namespace=test-host",
                        "ai-chat-kit.ai.mcp-jwt-v1.issuer=test-issuer",
                        "ai-chat-kit.ai.mcp-jwt-v1.audience=test-audience",
                        "ai-chat-kit.ai.mcp-jwt-v1.endpoints[0].endpoint-code=test-endpoint",
                        "ai-chat-kit.ai.mcp-jwt-v1.endpoints[0].permission=ai:resource:read",
                        "ai-chat-kit.ai.mcp-jwt-v1.keys.public-key=" + rsaPublicKey())
                .run(context -> {
                    assertThat(context)
                            .hasNotFailed()
                            .hasSingleBean(AiMcpV1KeySource.class)
                            .hasSingleBean(AiMcpV1VerificationService.class)
                            .doesNotHaveBean(AiRuntimeActivation.class);
                    assertThat(context.getBean(AiMcpV1KeySource.class))
                            .isInstanceOf(AiConfiguredMcpV1KeySource.class);
                });
    }

    /**
     * 防止签发兼容开关单独创建授权服务：没有 @EnableAiChatKit 的宿主必须保持关闭。
     */
    @Test
    void signingSwitchCannotActivateAuthorizationWithoutRuntimeMarker() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AiMcpV1SigningAutoConfiguration.class))
                .withUserConfiguration(HostIdentityPorts.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=true",
                        "ai-chat-kit.ai.starter.namespace=test-host",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=plain-app",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=ai:chat")
                .run(context -> assertThat(context).doesNotHaveBean(AiMcpV1AuthorizationService.class));
    }

    /**
     * 防止把普通无工具聊天错误绑定到 JWT 密钥：显式授权只需真实身份、会话、权限和应用策略。
     */
    @Test
    void authorizationSwitchAllowsPlainApplicationWithoutSigningKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AiMcpV1SigningAutoConfiguration.class))
                .withUserConfiguration(HostIdentityPorts.class, ActivatedRuntime.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=true",
                        "ai-chat-kit.ai.starter.namespace=test-host",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=plain-app",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=ai:chat")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiMcpV1AuthorizationService.class);
                    AiInvocationAuthorizationResult result = context.getBean(AiMcpV1AuthorizationService.class)
                            .authorize(request("plain-app", null, java.util.Collections.emptyList()));
                    assertThat(result.hasToolCredential()).isFalse();
                });
    }

    /**
     * 防止工具应用在缺少主体映射或密钥时降级成无凭据成功。
     */
    @Test
    void toolApplicationRejectsMissingSubjectAndKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AiMcpV1SigningAutoConfiguration.class))
                .withUserConfiguration(HostIdentityPorts.class, ActivatedRuntime.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=true",
                        "ai-chat-kit.ai.starter.namespace=test-host",
                        "ai-chat-kit.ai.mcp-jwt-v1.issuer=test-issuer",
                        "ai-chat-kit.ai.mcp-jwt-v1.audience=test-audience",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=tool-app",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=ai:tool",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].mcp-id=mcp-1",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].tool-ids[0]=tool-1",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].endpoint-codes[0]=endpoint-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiMcpV1AuthorizationService.class);
                    assertThatThrownBy(() -> context.getBean(AiMcpV1AuthorizationService.class)
                            .authorize(request("tool-app", "mcp-1", java.util.Collections.singletonList("tool-1"))))
                            .isInstanceOf(IllegalStateException.class);
                });
    }

    /** 授权开关不能隐式打开工具 JWT 签发，即使宿主碰巧提供了主体和密钥 Bean。 */
    @Test
    void authorizationWithoutSigningNeverTouchesToolSubjectOrKey() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AiMcpV1SigningAutoConfiguration.class))
                .withUserConfiguration(HostIdentityPorts.class, ToolPorts.class, ActivatedRuntime.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=false",
                        "ai-chat-kit.ai.starter.namespace=test-host",
                        "ai-chat-kit.ai.mcp-jwt-v1.issuer=test-issuer",
                        "ai-chat-kit.ai.mcp-jwt-v1.audience=test-audience",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=plain-app",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=ai:chat",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[1].app-id=tool-app",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[1].permission=ai:tool",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[1].mcp-id=mcp-1",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[1].tool-ids[0]=tool-1",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[1].endpoint-codes[0]=endpoint-1")
                .run(context -> {
                    AiMcpV1AuthorizationService service = context.getBean(AiMcpV1AuthorizationService.class);
                    assertThat(service.authorize(request("plain-app", null, java.util.Collections.emptyList()))
                            .hasToolCredential()).isFalse();
                    assertThatThrownBy(() -> service.authorize(
                            request("tool-app", "mcp-1", java.util.Collections.singletonList("tool-1"))))
                            .isInstanceOf(IllegalStateException.class);
                    verifyNoInteractions(context.getBean(AiMcpV1SubjectPort.class),
                            context.getBean(AiMcpV1KeySource.class));
                });
    }

    private AiInvocationAuthorizationRequest request(String appId, String mcpId, java.util.List<String> tools) {
        return new AiInvocationAuthorizationRequest(
                new AiInvocationContext("test-host", "1", "7", "invocation-1"), appId, mcpId, tools);
    }

    private String rsaPublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
    }

    @Configuration(proxyBeanMethods = false)
    static class HostIdentityPorts {
        @Bean
        AiInvocationContextPort invocationContexts() {
            return actor -> new AiInvocationContext("test-host", "1", actor, "invocation-1");
        }

        @Bean
        AiHostSessionPort hostSessions() {
            return new AiHostSessionPort() {
                @Override
                public AiHostSession currentSession(AiInvocationContext context) { return session(context); }

                @Override
                public AiHostSession checkSession(AiInvocationContext context, String sessionId) {
                    return session(context);
                }

                private AiHostSession session(AiInvocationContext context) {
                    return new AiHostSession(context.getNamespace(), context.getTenantId(), context.getActorId(),
                            "session-1", System.currentTimeMillis() + 60_000L);
                }
            };
        }

        @Bean
        AiHostPermissionPort hostPermissions() {
            return new AiHostPermissionPort() {
                @Override
                public boolean hasPermission(AiInvocationContext context, String permission) { return true; }

                @Override
                public boolean isPlatformAdministrator(AiInvocationContext context) { return false; }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ActivatedRuntime {
        @Bean
        AiRuntimeActivation aiRuntimeActivation() { return new AiRuntimeActivation(); }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "io.github.yoyocw.aichatkit.ai.adapter.mcp.v1",
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test(\\$.*)?"))
    static class BroadMcpScan {
    }

    @Configuration(proxyBeanMethods = false)
    static class VerificationSubjectPort {
        @Bean
        AiMcpV1SubjectPort subjects() { return mock(AiMcpV1SubjectPort.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class ToolPorts {
        @Bean
        AiMcpV1SubjectPort subjects() { return mock(AiMcpV1SubjectPort.class); }

        @Bean
        AiMcpV1KeySource keys() { return mock(AiMcpV1KeySource.class); }
    }
}
