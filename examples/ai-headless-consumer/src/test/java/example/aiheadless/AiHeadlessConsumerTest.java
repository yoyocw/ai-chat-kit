package example.aiheadless;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import io.github.yoyocw.aichatkit.ai.starter.host.*;
import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionMetrics;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.*;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.*;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.*;
import io.github.yoyocw.aichatkit.module.ai.contract.model.*;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiSingleChatExecutor;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

/** Real candidate JARs and PostgreSQL; identity, authorization, catalog and model are controlled fixtures. */
class AiHeadlessConsumerTest {
    private static final String NS = "headless-consumer", TENANT = "fixture-tenant", ACTOR = "fixture-actor";
    private static final String APP = "0123456789abcdef0123456789abcdef";
    private static final List<String> MEMBERS = Arrays.asList("ARCHITECT", "REVIEWER");

    @Test void actualClasspathExcludesOptionalTransportAndProviderAndUsesFourCandidateJars() {
        ClassLoader loader = getClass().getClassLoader();
        for (String absent : Arrays.asList("org.springframework.web.client.RestTemplate",
                "org.springframework.web.servlet.DispatcherServlet", "javax.servlet.Servlet",
                "jakarta.servlet.Servlet", "okhttp3.OkHttpClient",
                "io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient")) {
            assertThatThrownBy(() -> Class.forName(absent, false, loader)).isInstanceOf(ClassNotFoundException.class);
        }
        assertCandidateJar(AiInvocationContext.class, "contract");
        assertCandidateJar(AiSingleChatExecutor.class, "engine");
        assertCandidateJar(EnableAiChatKit.class, "starter");
        assertCandidateJar(AiJdbcResources.class, "adapter-mybatis-plus");
    }

    @Test void absentAnnotationAndDisabledEngineNeverCreateAiResources() {
        assertInactive(PlainHost.class, "true");
        assertInactive(DisabledHost.class, "false");
    }

    @Test @EnabledIfEnvironmentVariable(named = "AI_TEST_POSTGRES_URL", matches = ".+")
    void singlePrepareCommitsBeforeModelAndCompletesThroughNeutralEvents() throws Exception {
        withHost((context, db) -> {
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            ControlledAuthorization authorization = context.getBean(ControlledAuthorization.class);
            int previous = authorization.calls.get();
            ControlledModel model = context.getBean(ControlledModel.class);
            model.onInvoke = id -> assertTerminal(db, id, 0);
            AiPreparedExecution prepared = context.getBean(AiHostSingleChatService.class).prepare(request(conversation), ACTOR);
            long message = assistantId(db, conversation);
            assertTerminal(db, message, 0);
            assertThat(model.calls.get()).isZero();
            assertThat(authorization.calls.get() - previous).isEqualTo(1);
            List<String> events = new ArrayList<>();
            List<Map<String, Object>> done = new ArrayList<>();
            prepared.consume((event, data) -> {
                events.add(event);
                if ("done".equals(event)) { assertTerminal(db, message, 1); done.add(data); }
            });
            assertThat(events).contains("delta").endsWith("done");
            assertThat(done).hasSize(1);
            assertThat(done.get(0).get("status")).isEqualTo(1);
            assertThat(model.calls.get()).isEqualTo(1);
            assertThat(authorization.calls.get() - previous).isEqualTo(1);
            assertThat(db.queryForList("SELECT content FROM ai_runtime_message WHERE conversation_id=? ORDER BY id",
                    String.class, conversation)).containsExactly("fixture question", "headless single answer");
            assertThat(db.queryForObject("SELECT request_id FROM ai_runtime_execution WHERE message_id=?", String.class, message))
                    .isEqualTo("fixture-request");
            assertThatThrownBy(() -> prepared.consume((event, data) -> {})).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test @EnabledIfEnvironmentVariable(named = "AI_TEST_POSTGRES_URL", matches = ".+")
    void groupPreparePersistsAllMemberRepliesBeforePublishingMemberEvents() throws Exception {
        withHost((context, db) -> {
            AiHostGroupChatService group = context.getBean(AiHostGroupChatService.class);
            ControlledAuthorization authorization = context.getBean(ControlledAuthorization.class);
            long conversation = group.create("fixture group", MEMBERS, ACTOR);
            int previous = authorization.calls.get();
            ControlledModel model = context.getBean(ControlledModel.class);
            model.onInvoke = id -> assertTerminal(db, id, 0);
            AiPreparedExecution prepared = group.prepare(conversation, "fixture question", ACTOR);
            long message = assistantId(db, conversation);
            assertThat(model.calls.get()).isZero();
            assertThat(authorization.calls.get() - previous).isEqualTo(1);
            List<String> speakers = new ArrayList<>();
            List<Map<String, Object>> done = new ArrayList<>();
            prepared.consume((event, data) -> {
                if ("speaker-start".equals(event)) {
                    assertThat(db.queryForList("SELECT status FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                            Integer.class, conversation)).containsExactly(1, 1, 1);
                    speakers.add((String) data.get("speakerCode"));
                }
                if ("done".equals(event)) { done.add(data); }
            });
            assertTerminal(db, message, 1);
            assertThat(speakers).containsExactly("ARCHITECT", "REVIEWER", "ORCHESTRATOR");
            assertThat(done).hasSize(1);
            assertThat(done.get(0).get("status")).isEqualTo(1);
            assertThat(db.queryForList("SELECT content FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                    String.class, conversation)).containsExactly("headless design", "headless review", "headless summary");
            assertThat(db.queryForList("SELECT round_no FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                    Integer.class, conversation)).containsExactly(1, 2, 3);
            assertThat(model.calls.get()).isEqualTo(1);
            assertThat(authorization.calls.get() - previous).isEqualTo(1);
        });
    }

    @Test @EnabledIfEnvironmentVariable(named = "AI_TEST_POSTGRES_URL", matches = ".+")
    void deniedApplicationAuthorizationCannotPrepareMessagesOrInvokeModelInEitherMode() throws Exception {
        withHost((context, db) -> {
            long single = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            AiHostGroupChatService group = context.getBean(AiHostGroupChatService.class);
            long multi = group.create("fixture group", MEMBERS, ACTOR);
            ControlledAuthorization authorization = context.getBean(ControlledAuthorization.class);
            int previous = authorization.calls.get();
            authorization.allowed = false;
            assertThatThrownBy(() -> context.getBean(AiHostSingleChatService.class).prepare(request(single), ACTOR))
                    .isInstanceOf(IllegalStateException.class).hasMessage("fixture application denied");
            assertThatThrownBy(() -> group.prepare(multi, "fixture question", ACTOR))
                    .isInstanceOf(IllegalStateException.class).hasMessage("fixture application denied");
            assertThat(authorization.calls.get() - previous).isEqualTo(2);
            assertThat(context.getBean(ControlledModel.class).calls.get()).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM ai_runtime_message", Integer.class)).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM ai_runtime_execution", Integer.class)).isZero();
        });
    }

    private void assertInactive(Class<?> host, String enabled) {
        new ApplicationContextRunner().withUserConfiguration(host).withPropertyValues(
                "ai-chat-kit.ai.engine.enabled=" + enabled,
                "ai-chat-kit.ai.storage.type=postgresql", "ai-chat-kit.ai.storage.jdbc.mode=isolated",
                "ai-chat-kit.ai.storage.jdbc.jdbc-url=jdbc:postgresql://127.0.0.1:1/must_not_connect",
                "ai-chat-kit.ai.starter.namespace=" + NS, "ai-chat-kit.ai.starter.modes=SINGLE,GROUP").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("hostHealth")).isEqualTo("host-ready");
            assertThat(context).doesNotHaveBean(AiJdbcResources.class).doesNotHaveBean(AiExecutionPolicy.class);
            assertThat(context).doesNotHaveBean(AiTransactionExecutor.class).doesNotHaveBean(AiSingleChatExecutor.class);
            assertThat(context).doesNotHaveBean(AiHostSingleChatService.class).doesNotHaveBean(AiHostGroupChatService.class);
            assertThat(context).doesNotHaveBean(AiModelClient.class);
        });
    }

    private static void assertCandidateJar(Class<?> type, String module) {
        String location = type.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
        assertThat(location).endsWith("/ai-chat-kit-" + module + "-2.0.0-SNAPSHOT.jar");
    }

    private void withHost(Flow flow) throws Exception {
        String base = System.getenv("AI_TEST_POSTGRES_URL");
        if (base == null || !base.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/ai_component_test")) {
            throw new IllegalArgumentException("Only the dedicated loopback PostgreSQL database is allowed");
        }
        String schema = "ai_headless_" + UUID.randomUUID().toString().replace("-", "");
        AtomicReference<HikariDataSource> pool = new AtomicReference<>();
        try (Connection admin = DriverManager.getConnection(base, "postgres", ""); Statement ddl = admin.createStatement()) {
            ddl.execute("CREATE SCHEMA " + schema);
            try {
                admin.setSchema(schema);
                ScriptUtils.executeSqlScript(admin, new EncodedResource(new ClassPathResource("db/postgresql/ai-runtime.sql"), StandardCharsets.UTF_8));
                String url = base + "?currentSchema=" + schema;
                JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
                new ApplicationContextRunner().withUserConfiguration(EnabledHost.class).withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.starter.namespace=" + NS,
                        "ai-chat-kit.ai.starter.modes=SINGLE,GROUP", "ai-chat-kit.ai.storage.type=postgresql",
                        "ai-chat-kit.ai.storage.jdbc.mode=isolated", "ai-chat-kit.ai.storage.jdbc.jdbc-url=" + url,
                        "ai-chat-kit.ai.storage.jdbc.username=postgres", "ai-chat-kit.ai.storage.jdbc.password=",
                        "ai-chat-kit.ai.storage.jdbc.maximum-pool-size=3", "ai-chat-kit.ai.execution.scope=jdbc-explicit",
                        "ai-chat-kit.ai.applications.single.app-id=" + APP, "ai-chat-kit.ai.applications.group.app-id=" + APP)
                        .run(context -> {
                            assertThat(context).hasNotFailed();
                            pool.set((HikariDataSource) context.getBean(AiJdbcResources.class).source());
                            flow.run(context, db);
                        });
                assertThat(pool.get().isClosed()).isTrue();
            } finally {
                ddl.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static void assertTerminal(JdbcTemplate db, long id, int status) {
        // Each query uses an independent DriverManager connection, so uncommitted writes remain invisible.
        assertThat(db.queryForObject("SELECT status FROM ai_runtime_message WHERE id=?", Integer.class, id)).isEqualTo(status);
        assertThat(db.queryForObject("SELECT status FROM ai_runtime_execution WHERE message_id=?", Integer.class, id)).isEqualTo(status);
    }
    private static long assistantId(JdbcTemplate db, long conversation) {
        return db.queryForObject("SELECT id FROM ai_runtime_message WHERE conversation_id=? AND role='assistant'", Long.class, conversation);
    }
    private static AiSingleChatRequest request(long conversation) {
        return new AiSingleChatRequest() {
            public Long getConversationId() { return conversation; }
            public String getContent() { return "fixture question"; }
            public Boolean getMapEnabled() { return false; }
        };
    }
    private interface Flow { void run(AssertableApplicationContext context, JdbcTemplate db) throws Exception; }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    static class PlainHost { @Bean String hostHealth() { return "host-ready"; } }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableAiChatKit
    static class DisabledHost { @Bean String hostHealth() { return "host-ready"; } }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableAiChatKit
    @Import(Fixtures.class)
    static class EnabledHost { }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean FixedIdentity identity() { return new FixedIdentity(); }
        @Bean ControlledAuthorization authorization() { return new ControlledAuthorization(); }
        @Bean ControlledModel model() { return new ControlledModel(); }
        @Bean AiOriginContextPort origin() { return app -> null; }
        @Bean AiGroupAgentCatalogPort catalog() {
            return new AiGroupAgentCatalogPort() {
                private final List<AiGroupMemberSnapshot> members = Collections.unmodifiableList(Arrays.asList(
                        new AiGroupMemberSnapshot("ARCHITECT", "Architect", "Design"),
                        new AiGroupMemberSnapshot("REVIEWER", "Reviewer", "Review")));
                public List<AiGroupMemberSnapshot> listAvailable(AiInvocationContext context) { checkIdentity(context); return members; }
                public List<AiGroupMemberSnapshot> resolve(AiInvocationContext context, List<String> codes) {
                    checkIdentity(context);
                    if (!MEMBERS.equals(codes)) { throw new IllegalStateException("Unexpected fixture member selection"); }
                    return members;
                }
            };
        }
    }

    static class ControlledAuthorization implements AiInvocationAuthorizationPort {
        final AtomicInteger calls = new AtomicInteger();
        boolean allowed = true;
        public AiInvocationAuthorizationResult authorize(AiInvocationAuthorizationRequest request) {
            calls.incrementAndGet();
            assertThat(request.getNamespace()).isEqualTo(NS);
            assertThat(request.getTenantId()).isEqualTo(TENANT);
            assertThat(request.getActorId()).isEqualTo(ACTOR);
            assertThat(request.getInvocationId()).isNotEmpty();
            assertThat(request.getAppId()).isEqualTo(APP);
            assertThat(request.getToolIds()).isEmpty();
            if (!allowed) { throw new IllegalStateException("fixture application denied"); }
            return new AiInvocationAuthorizationResult(null);
        }
    }

    static class FixedIdentity implements AiInvocationContextPort, AiHostSessionPort, AiHostPermissionPort {
        private final Set<String> permissions = Arrays.stream(AiHostAction.values())
                .filter(action -> !action.isPlatformAdministratorRequired()).map(AiHostAction::getPermission).collect(Collectors.toSet());
        public AiInvocationContext captureCurrent() { return new AiInvocationContext(NS, TENANT, ACTOR, UUID.randomUUID().toString()); }
        public AiInvocationContext capture(String actor) {
            if (!ACTOR.equals(actor)) { throw new IllegalStateException("Unexpected fixture actor"); }
            return captureCurrent();
        }
        public AiHostSession currentSession(AiInvocationContext context) {
            checkIdentity(context);
            return new AiHostSession(NS, TENANT, ACTOR, "fixture-session", System.currentTimeMillis() + 60000);
        }
        public AiHostSession checkSession(AiInvocationContext context, String session) {
            if (!"fixture-session".equals(session)) { throw new IllegalStateException("Unexpected fixture session"); }
            return currentSession(context);
        }
        public boolean hasPermission(AiInvocationContext context, String permission) { checkIdentity(context); return permissions.contains(permission); }
        public boolean isPlatformAdministrator(AiInvocationContext context) { checkIdentity(context); return false; }
    }

    private static void checkIdentity(AiInvocationContext context) {
        if (context == null || !NS.equals(context.getNamespace()) || !TENANT.equals(context.getTenantId()) || !ACTOR.equals(context.getActorId())) {
            throw new IllegalStateException("Unexpected fixture identity scope");
        }
    }

    static class ControlledModel implements AiModelClient {
        final AtomicInteger calls = new AtomicInteger();
        LongConsumer onInvoke = id -> {};
        public boolean isConfigured(AiChatMode mode, String app) { return APP.equals(app); }
        public AiModelResult stream(AiModelRequest request, Consumer<AiModelEvent> events, BooleanSupplier generating) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(generating.getAsBoolean()).isTrue();
            calls.incrementAndGet();
            onInvoke.accept(request.getMessageId());
            AiExecutionMetrics metrics = new AiExecutionMetrics("fixture-request", "fixture-model", 5, 8, 0, 1L, 1L);
            if (request.getMode() == AiChatMode.SINGLE) {
                events.accept(AiModelEvent.delta("headless single answer"));
                return new AiModelResult("headless single answer", "fixture-request", "fixture-remote-session", "stop", null,
                        metrics, Collections.emptyList(), null);
            }
            assertThat(request.getMembers()).extracting("code").containsExactly("ARCHITECT", "REVIEWER");
            return new AiModelResult("headless summary", "fixture-request", "fixture-remote-session", "stop", null, metrics,
                    Arrays.asList(new AiModelGroupReply("ARCHITECT", "headless design"),
                            new AiModelGroupReply("REVIEWER", "headless review"),
                            new AiModelGroupReply("ORCHESTRATOR", "headless summary")), null);
        }
        public void cancel(AiChatMode mode, Long id) { }
    }
}
