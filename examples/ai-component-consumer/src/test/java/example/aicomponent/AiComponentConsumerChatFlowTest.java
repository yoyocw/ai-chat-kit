package example.aicomponent;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import io.github.yoyocw.aichatkit.ai.starter.host.*;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.*;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareGrant;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedConversation;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/** Real candidate JDBC, transactions, authorization, execution, parser and SSE; only model I/O is replaced. */
@EnabledIfEnvironmentVariable(named = "AI_TEST_POSTGRES_URL", matches = ".+")
class AiComponentConsumerChatFlowTest {
    private static final String NS = "consumer-flow", ACTOR = "test-actor";
    private static final String APP = "0123456789abcdef0123456789abcdef";
    private static final List<String> MEMBERS = Arrays.asList("ARCHITECT", "REVIEWER");

    @Test
    void singleSendCommitsThenCompletesHistoryManagementAndRevocableShare() throws Exception {
        withHost((context, db) -> {
            AiHostConversationManagementService management = context.getBean(AiHostConversationManagementService.class);
            long conversation = management.createSingle(ACTOR);
            ModelFixture model = context.getBean(ModelFixture.class);
            model.onInvoke = message -> assertTerminal(db, message, 0);
            StreamingResponseBody body = context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR);
            long message = assistantId(db, conversation);
            assertTerminal(db, message, 0);
            assertThat(model.calls.get()).isZero();
            String sse = write(body);
            assertThat(events(sse, "delta")).extracting(node -> node.path("content").asText()).contains("single answer");
            assertDone(sse, message, 1);
            assertTerminal(db, message, 1);
            assertThat(model.calls.get()).isEqualTo(1);
            assertThat(management.listMessages(AiChatMode.SINGLE, conversation, ACTOR))
                    .extracting("content").containsExactly("test question", "single answer");
            assertThat(db.queryForObject("SELECT request_id FROM ai_runtime_execution WHERE message_id=?", String.class, message))
                    .isEqualTo("fixture-request");
            checkManagementAndShare(context, db, AiChatMode.SINGLE, conversation, 2);
        });
    }

    @Test
    void groupWorkflowPersistsOrderedRepliesAndMemberSnapshotsBeforeSharing() throws Exception {
        withHost((context, db) -> {
            AiHostGroupChatService group = context.getBean(AiHostGroupChatService.class);
            long conversation = group.create("group", MEMBERS, ACTOR);
            context.getBean(ModelFixture.class).onInvoke = message -> assertTerminal(db, message, 0);
            StreamingResponseBody body = group.send(conversation, "test question", ACTOR);
            long message = assistantId(db, conversation);
            assertTerminal(db, message, 0);
            String sse = write(body);
            assertDone(sse, message, 1);
            assertTerminal(db, message, 1);
            assertThat(events(sse, "speaker-start")).extracting(node -> node.path("speakerCode").asText())
                    .containsExactly("ARCHITECT", "REVIEWER", "ORCHESTRATOR");
            assertThat(events(sse, "speaker-done")).hasSize(3);
            assertThat(events(sse, "delta")).extracting(node -> node.path("content").asText())
                    .containsExactly("design answer", "review answer", "final summary");
            assertThat(db.queryForList("SELECT speaker_code FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                    String.class, conversation)).containsExactly("ARCHITECT", "REVIEWER", "ORCHESTRATOR");
            assertThat(db.queryForList("SELECT round_no FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                    Integer.class, conversation)).containsExactly(1, 2, 3);
            AiHostConversationManagementService management = context.getBean(AiHostConversationManagementService.class);
            assertThat(management.listMessages(AiChatMode.GROUP, conversation, ACTOR)).extracting("content")
                    .containsExactly("test question", "design answer", "review answer", "final summary");
            management.updateGroupMembers(conversation, Arrays.asList("REVIEWER", "ARCHITECT"), ACTOR);
            assertThat(db.queryForList("SELECT agent_name FROM ai_runtime_member WHERE conversation_id=? AND deleted=false ORDER BY sort_order",
                    String.class, conversation)).containsExactly("Reviewer", "Architect");
            checkManagementAndShare(context, db, AiChatMode.GROUP, conversation, 4);
        });
    }

    @Test
    void stopCommitsBeforeModelCancellationAndLateFailurePreservesStoppedSqlAndSse() throws Exception {
        withHost((context, db) -> {
            ModelFixture model = context.getBean(ModelFixture.class);
            model.block = true;
            model.onCancel = message -> assertTerminal(db, message, 2);
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            AiHostSingleChatService single = context.getBean(AiHostSingleChatService.class);
            StreamingResponseBody body = single.send(request(conversation), ACTOR);
            long message = assistantId(db, conversation);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> stream = executor.submit(() -> write(body));
                assertThat(model.entered.await(10, TimeUnit.SECONDS)).as("model started without polling or sleep").isTrue();
                single.stop(message, ACTOR);
                assertThat(model.cancellations.get()).isEqualTo(1);
                String sse = stream.get(10, TimeUnit.SECONDS);
                assertDone(sse, message, 2);
                assertThat(events(sse, "error")).isEmpty();
                assertTerminal(db, message, 2);
            } finally {
                model.release.countDown();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        });
    }

    private void checkManagementAndShare(AssertableWebApplicationContext context, JdbcTemplate db,
            AiChatMode mode, long conversation, int messages) {
        AiHostConversationManagementService management = context.getBean(AiHostConversationManagementService.class);
        management.rename(mode, conversation, "reviewed title", ACTOR);
        management.pin(mode, conversation, true, ACTOR);
        assertThat(management.listConversations(mode, ACTOR)).extracting("title", "pinned")
                .containsExactly(tuple("reviewed title", true));
        AiHostConversationShareService shares = context.getBean(AiHostConversationShareService.class);
        AiPublicConversationShareService publicShares = context.getBean(AiPublicConversationShareService.class);
        AiShareGrant grant = shares.issue(mode, conversation, 1, ACTOR);
        AiSharedConversation shared = publicShares.read(mode, grant.getShareCode());
        assertThat(shared.getTitle()).isEqualTo("reviewed title");
        assertThat(shared.getMessages()).hasSize(messages);
        if (mode == AiChatMode.GROUP) {
            assertThat(shared.getMemberCodes()).containsExactly("REVIEWER", "ARCHITECT");
            assertThat(shared.getMembers()).extracting("name").containsExactly("Reviewer", "Architect");
        }
        assertThat(db.queryForObject("SELECT share_access_count FROM ai_runtime_conversation WHERE id=?", Long.class, conversation)).isEqualTo(1L);
        shares.revoke(mode, conversation, ACTOR);
        assertThatThrownBy(() -> publicShares.read(mode, grant.getShareCode())).isInstanceOf(AiExecutionException.class);
        assertThat(db.queryForObject("SELECT share_status FROM ai_runtime_conversation WHERE id=?", Integer.class, conversation)).isZero();
        management.pin(mode, conversation, false, ACTOR);
        assertThat(db.queryForObject("SELECT pinned_at IS NULL FROM ai_runtime_conversation WHERE id=?", Boolean.class, conversation)).isTrue();
        management.delete(mode, conversation, ACTOR);
        assertThat(management.listConversations(mode, ACTOR)).isEmpty();
        assertThat(db.queryForObject("SELECT deleted FROM ai_runtime_conversation WHERE id=?", Boolean.class, conversation)).isTrue();
    }

    private static void assertTerminal(JdbcTemplate db, long message, int status) {
        // This separate DriverManager connection cannot accidentally see an uncommitted AI transaction.
        assertThat(db.queryForObject("SELECT status FROM ai_runtime_message WHERE id=?", Integer.class, message)).isEqualTo(status);
        assertThat(db.queryForObject("SELECT status FROM ai_runtime_execution WHERE message_id=?", Integer.class, message)).isEqualTo(status);
    }
    private static long assistantId(JdbcTemplate db, long conversation) {
        return db.queryForObject("SELECT id FROM ai_runtime_message WHERE conversation_id=? AND role='assistant'", Long.class, conversation);
    }
    private static AiSingleChatRequest request(long conversation) {
        return new AiSingleChatRequest() {
            public Long getConversationId() { return conversation; }
            public String getContent() { return "test question"; }
            public Boolean getMapEnabled() { return false; }
        };
    }
    private static String write(StreamingResponseBody body) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); body.writeTo(output);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
    private static List<JsonNode> events(String sse, String event) throws IOException {
        List<JsonNode> found = new ArrayList<>();
        for (String frame : sse.replace("\r\n", "\n").split("\n\n")) {
            if (frame.startsWith("event:" + event + "\n")) {
                found.add(new ObjectMapper().readTree(frame.substring(frame.indexOf("data:") + 5)));
            }
        }
        return found;
    }
    private static void assertDone(String sse, long message, int status) throws IOException {
        List<JsonNode> done = events(sse, "done");
        assertThat(done).hasSize(1);
        assertThat(done.get(0).path("messageId").asLong()).isEqualTo(message);
        assertThat(done.get(0).path("status").asInt()).isEqualTo(status);
    }

    private void withHost(Flow flow) throws Exception {
        String base = System.getenv("AI_TEST_POSTGRES_URL");
        if (base == null || !base.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/ai_component_test")) {
            throw new IllegalArgumentException("Only the dedicated loopback PostgreSQL database is allowed");
        }
        String schema = "ai_consumer_flow_" + UUID.randomUUID().toString().replace("-", "");
        AtomicReference<HikariDataSource> pool = new AtomicReference<>();
        try (Connection admin = DriverManager.getConnection(base, "postgres", ""); Statement ddl = admin.createStatement()) {
            ddl.execute("CREATE SCHEMA " + schema);
            try {
                admin.setSchema(schema);
                ScriptUtils.executeSqlScript(admin, new EncodedResource(new ClassPathResource("db/postgresql/ai-runtime.sql"), StandardCharsets.UTF_8));
                String url = base + "?currentSchema=" + schema;
                JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""));
                new WebApplicationContextRunner().withUserConfiguration(Host.class).withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=true",
                        "ai-chat-kit.ai.starter.namespace=" + NS, "ai-chat-kit.ai.starter.modes=SINGLE,GROUP",
                        "ai-chat-kit.ai.storage.type=postgresql", "ai-chat-kit.ai.storage.jdbc.mode=isolated",
                        "ai-chat-kit.ai.storage.jdbc.jdbc-url=" + url, "ai-chat-kit.ai.storage.jdbc.username=postgres",
                        "ai-chat-kit.ai.storage.jdbc.password=", "ai-chat-kit.ai.storage.jdbc.maximum-pool-size=3",
                        "ai-chat-kit.ai.execution.scope=jdbc-explicit", "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=false", "ai-chat-kit.ai.mcp-jwt-v1.verification-enabled=false",
                        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=" + APP, "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=test:ai:invoke",
                        "ai-chat-kit.ai.applications.single.app-id=" + APP, "ai-chat-kit.ai.applications.group.app-id=" + APP,
                        "ai-chat-kit.ai.group.agents[0].code=ARCHITECT", "ai-chat-kit.ai.group.agents[0].name=Architect",
                        "ai-chat-kit.ai.group.agents[0].role=Design", "ai-chat-kit.ai.group.agents[0].enabled=true",
                        "ai-chat-kit.ai.group.agents[1].code=REVIEWER", "ai-chat-kit.ai.group.agents[1].name=Reviewer",
                        "ai-chat-kit.ai.group.agents[1].role=Review", "ai-chat-kit.ai.group.agents[1].enabled=true")
                        .run(context -> {
                            assertThat(context).hasNotFailed();
                            pool.set((HikariDataSource) context.getBean(AiJdbcResources.class).source());
                            flow.run(context, db);
                        });
                assertThat(pool.get().isClosed()).isTrue();
            } finally {
                // The identifier is generated here, never supplied by an environment or another test.
                ddl.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
    private interface Flow { void run(AssertableWebApplicationContext context, JdbcTemplate db) throws Exception; }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableAiChatKit
    static class Host {
        @Bean FixedIdentity identity() { return new FixedIdentity(); }
        @Bean AiOriginContextPort origin() { return app -> null; }
        @Bean ModelFixture model() { return new ModelFixture(); }
    }
    static class FixedIdentity implements AiInvocationContextPort, AiHostSessionPort, AiHostPermissionPort {
        private final Set<String> allowed = Arrays.stream(AiHostAction.values())
                .filter(action -> !action.isPlatformAdministratorRequired()).map(AiHostAction::getPermission).collect(Collectors.toSet());
        FixedIdentity() { allowed.add("test:ai:invoke"); }
        public AiInvocationContext captureCurrent() { return new AiInvocationContext(NS, "test-tenant", ACTOR, UUID.randomUUID().toString()); }
        public AiInvocationContext capture(String actor) {
            if (!ACTOR.equals(actor)) { throw new IllegalStateException("Unexpected test actor"); } return captureCurrent();
        }
        public AiHostSession currentSession(AiInvocationContext context) {
            if (!NS.equals(context.getNamespace()) || !"test-tenant".equals(context.getTenantId()) || !ACTOR.equals(context.getActorId())) {
                throw new IllegalStateException("Unexpected test identity scope");
            }
            return new AiHostSession(NS, "test-tenant", ACTOR, "test-session", System.currentTimeMillis() + 60000);
        }
        public AiHostSession checkSession(AiInvocationContext context, String session) {
            if (!"test-session".equals(session)) { throw new IllegalStateException("Unexpected test session"); } return currentSession(context);
        }
        public boolean hasPermission(AiInvocationContext context, String permission) { currentSession(context); return allowed.contains(permission); }
        public boolean isPlatformAdministrator(AiInvocationContext context) { return false; }
    }
    static class ModelFixture extends BailianClient {
        final AtomicInteger calls = new AtomicInteger(), cancellations = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean block;
        LongConsumer onInvoke = id -> { }, onCancel = id -> { };
        ModelFixture() { super(new BailianProperties()); }
        @Override public boolean isConfigured(String app) { return APP.equals(app); }
        @Override public boolean isGroupWorkflowConfigured(String app) { return APP.equals(app); }
        @Override public BailianStreamResult stream(Long id, String app, String prompt, String session,
                Map<String, Object> params, Consumer<BailianStreamEvent> consumer, BooleanSupplier generating) throws IOException {
            calls.incrementAndGet(); onInvoke.accept(id);
            consumer.accept(BailianStreamEvent.delta("single answer")); entered.countDown();
            if (block) {
                try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IOException("Test cancellation timed out"); } }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException(ex); }
                throw new IOException("Test model cancelled");
            }
            return result("single answer");
        }
        @Override public BailianStreamResult streamGroupWorkflow(Long id, String app, String prompt, String session,
                Map<String, Object> params, BooleanSupplier generating) {
            calls.incrementAndGet(); onInvoke.accept(id);
            return result("{\"replies\":[{\"speakerCode\":\"ARCHITECT\",\"content\":\"design answer\"},"
                    + "{\"speakerCode\":\"REVIEWER\",\"content\":\"review answer\"},"
                    + "{\"speakerCode\":\"ORCHESTRATOR\",\"content\":\"final summary\"}]}");
        }
        @Override public boolean cancel(Long id) {
            onCancel.accept(id); cancellations.incrementAndGet(); release.countDown(); return true;
        }
        @Override public boolean cancelGroupWorkflow(Long id) { return true; }
        private BailianStreamResult result(String content) {
            BailianStreamResult result = new BailianStreamResult(); result.setContent(content);
            result.setRequestId("fixture-request"); result.setSessionId("fixture-session"); result.setFinishReason("stop");
            result.setModelNames("fixture-model"); result.setInputTokens(7); result.setOutputTokens(9); return result;
        }
    }
}
