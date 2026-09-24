package example.aicomponent;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1AuthorizationService;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebErrorAdvice;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebGroupController;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebSingleController;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostGroupChatService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostSingleChatService;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full candidate auto-configuration and real pool lifecycle; no model request or business-table write. */
@EnabledIfEnvironmentVariable(named = "AI_TEST_POSTGRES_URL", matches = ".+")
class AiComponentConsumerModesLifecycleTest {
    private static final String NAMESPACE = "consumer-lifecycle";
    private static final String APP = "0123456789abcdef0123456789abcdef";

    @Test
    void groupModeClosesItsPoolThenDisabledRestartKeepsHostHealthy() {
        assertEnabledThenDisabled(false);
    }

    @Test
    void bothModesCloseTheirPoolThenDisabledRestartKeepsHostHealthy() {
        assertEnabledThenDisabled(true);
    }

    @Test
    void allAutoConfigurationsWithoutEnableAnnotationLeaveHostHealthy() {
        runner(PlainHost.class).withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=true")
                .run(context -> {
                    assertInactive(context);
                    assertThat(context).doesNotHaveBean(AiRuntimeActivation.class);
                });
    }

    private void assertEnabledThenDisabled(boolean bothModes) {
        AtomicReference<HikariDataSource> activePool = new AtomicReference<>();
        WebApplicationContextRunner enabled = runner(EnabledHost.class)
                .withUserConfiguration(HostIdentityFixture.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true",
                        "ai-chat-kit.ai.starter.modes=" + (bothModes ? "SINGLE,GROUP" : "GROUP"));
        enabled.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AiRuntimeActivation.class)
                    .hasSingleBean(AiHostGroupChatService.class).hasSingleBean(AiWebGroupController.class)
                    .hasSingleBean(AiMcpV1AuthorizationService.class).hasSingleBean(BailianClient.class);
            if (bothModes) {
                assertThat(context).hasSingleBean(AiHostSingleChatService.class).hasSingleBean(AiWebSingleController.class);
            } else {
                assertThat(context).doesNotHaveBean(AiWebSingleController.class);
            }
            assertThat(routeCount(context, AiWebGroupController.class)).isEqualTo(13);
            assertThat(routeCount(context, AiWebSingleController.class)).isEqualTo(bothModes ? 11 : 0);
            assertHostHealthy(context);
            assertThat(context).doesNotHaveBean(DataSource.class).doesNotHaveBean(PlatformTransactionManager.class);
            HikariDataSource pool = (HikariDataSource) context.getBean(AiJdbcResources.class).source();
            activePool.set(pool);
            // Open a real connection without creating, querying or modifying any application's schema.
            try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT current_database()")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("ai_component_test");
            }
            assertThat(pool.isClosed()).isFalse();
            assertThat(pool.getHikariPoolMXBean().getTotalConnections()).isGreaterThan(0);
        });
        assertThat(activePool.get()).isNotNull();
        assertThat(activePool.get().isClosed()).as("the enabled context owns and closes its connected pool").isTrue();

        // A fresh context models H1 disable-and-restart. It deliberately has no identity fixture.
        runner(EnabledHost.class).withPropertyValues("ai-chat-kit.ai.engine.enabled=false",
                        "ai-chat-kit.ai.starter.modes=" + (bothModes ? "SINGLE,GROUP" : "GROUP"),
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=true")
                .run(context -> {
                    assertInactive(context);
                    assertThat(context).hasSingleBean(AiRuntimeActivation.class);
                });
        assertThat(activePool.get().isClosed()).isTrue();
    }

    private WebApplicationContextRunner runner(Class<?> host) {
        String url = System.getenv("AI_TEST_POSTGRES_URL");
        Matcher address = Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{1,5})/ai_component_test")
                .matcher(url == null ? "" : url);
        if (!address.matches() || Integer.parseInt(address.group(1)) < 1 || Integer.parseInt(address.group(1)) > 65535) {
            throw new IllegalArgumentException("Lifecycle tests require the dedicated loopback PostgreSQL database");
        }
        return new WebApplicationContextRunner().withUserConfiguration(host).withPropertyValues(
                "ai-chat-kit.ai.web.enabled=true", "ai-chat-kit.ai.starter.namespace=" + NAMESPACE,
                "ai-chat-kit.ai.storage.type=postgresql", "ai-chat-kit.ai.storage.jdbc.mode=isolated",
                "ai-chat-kit.ai.storage.jdbc.jdbc-url=" + url, "ai-chat-kit.ai.storage.jdbc.username=postgres",
                "ai-chat-kit.ai.storage.jdbc.password=", "ai-chat-kit.ai.storage.jdbc.maximum-pool-size=2",
                "ai-chat-kit.ai.execution.scope=jdbc-explicit", "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=true",
                "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=false", "ai-chat-kit.ai.mcp-jwt-v1.verification-enabled=false",
                "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=" + APP,
                "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=test:ai:invoke",
                "ai-chat-kit.ai.applications.single.app-id=" + APP, "ai-chat-kit.ai.applications.group.app-id=" + APP,
                "ai-chat-kit.ai.group.agents[0].code=ARCHITECT", "ai-chat-kit.ai.group.agents[0].name=Architect",
                "ai-chat-kit.ai.group.agents[0].role=Design the system", "ai-chat-kit.ai.group.agents[0].enabled=true",
                "ai-chat-kit.ai.group.agents[1].code=REVIEWER", "ai-chat-kit.ai.group.agents[1].name=Reviewer",
                "ai-chat-kit.ai.group.agents[1].role=Review the design", "ai-chat-kit.ai.group.agents[1].enabled=true");
    }

    private void assertInactive(AssertableWebApplicationContext context) throws Exception {
        assertThat(context).hasNotFailed().doesNotHaveBean(AiJdbcResources.class).doesNotHaveBean(BailianClient.class)
                .doesNotHaveBean(AiMcpV1AuthorizationService.class).doesNotHaveBean(AiWebSingleController.class)
                .doesNotHaveBean(AiWebGroupController.class).doesNotHaveBean(AiWebErrorAdvice.class)
                .doesNotHaveBean(DataSource.class).doesNotHaveBean(PlatformTransactionManager.class);
        assertThat(routeCount(context, AiWebSingleController.class)).isZero();
        assertThat(routeCount(context, AiWebGroupController.class)).isZero();
        assertHostHealthy(context);
    }

    private long routeCount(AssertableWebApplicationContext context, Class<?> controller) {
        return context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().values().stream()
                .filter(handler -> controller.isAssignableFrom(handler.getBeanType())).count();
    }

    private void assertHostHealthy(AssertableWebApplicationContext context) throws Exception {
        MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build()
                .perform(get("/host/health")).andExpect(status().isOk()).andExpect(content().string("host-ready"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import(HostHealthController.class)
    static class PlainHost { }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAiChatKit
    @Import(PlainHost.class)
    static class EnabledHost { }

    @TestConfiguration(proxyBeanMethods = false)
    static class HostIdentityFixture {
        @Bean FixedIdentity fixtureIdentity() { return new FixedIdentity(); }
        @Bean AiOriginContextPort ordinaryOrigin() { return appId -> null; }
    }

    /** Test-only opaque identity, with no dependence on Platform or a production authentication bypass. */
    static class FixedIdentity implements AiInvocationContextPort, AiHostSessionPort, AiHostPermissionPort {
        @Override public AiInvocationContext captureCurrent() {
            return new AiInvocationContext(NAMESPACE, "test-tenant", "test-actor", "test-invocation");
        }
        @Override public AiInvocationContext capture(String actor) {
            if (!"test-actor".equals(actor)) { throw new IllegalStateException("Unexpected test actor"); }
            return captureCurrent();
        }
        @Override public AiHostSession currentSession(AiInvocationContext context) {
            return new AiHostSession(NAMESPACE, "test-tenant", "test-actor", "test-session", System.currentTimeMillis() + 60000);
        }
        @Override public AiHostSession checkSession(AiInvocationContext context, String session) {
            if (!"test-session".equals(session)) { throw new IllegalStateException("Unexpected test session"); }
            return currentSession(context);
        }
        @Override public boolean hasPermission(AiInvocationContext context, String permission) { return "test:ai:invoke".equals(permission); }
        @Override public boolean isPlatformAdministrator(AiInvocationContext context) { return false; }
    }
}
