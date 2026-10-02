package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResourceMode;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResourceProperties;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareLease;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedConversation;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedMessage;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupReplyRecord;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPrepareCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import com.zaxxer.hikari.HikariDataSource;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcExecutionAuditAdapter;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcMessageOriginAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionMetrics;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL coverage, opt-in only. The caller supplies a disposable loopback instance;
 * every test creates and drops only its own unpredictable schema, never public or Platform tables.
 */
@EnabledIfEnvironmentVariable(named = "AI_TEST_POSTGRES_URL", matches = ".+")
class AiJdbcPostgresIntegrationTest {
    private static final String NAMESPACE = "postgres-integration";
    private static final Pattern ALLOWED_URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{1,5})/ai_component_test");
    private static final Pattern OWN_SCHEMA = Pattern.compile("ai_jdbc_it_[0-9a-f]{32}");
    private final AiInvocationContext owner = actor(NAMESPACE, "tenant-a", "actor-a");
    private String baseUrl;
    private String schema;
    private boolean schemaCreated;
    private AiJdbcResources resources;
    private AiTransactionExecutor transactions;
    private AiJdbcAccess access;
    private AiJdbcChatRepository chats;
    private AiJdbcConversationRepository conversations;
    private AiJdbcShareRepository shares;
    private AiJdbcGroupRepository groups;
    private JdbcTemplate independent;

    @BeforeEach
    void createPrivateSchemaAndApplyActualDdl() throws Exception {
        baseUrl = System.getenv("AI_TEST_POSTGRES_URL");
        Matcher address = ALLOWED_URL.matcher(baseUrl == null ? "" : baseUrl);
        if (!address.matches() || Integer.parseInt(address.group(1)) < 1
                || Integer.parseInt(address.group(1)) > 65535) {
            throw new IllegalArgumentException("PostgreSQL integration tests require the dedicated loopback test database");
        }
        schema = "ai_jdbc_it_" + UUID.randomUUID().toString().replace("-", "");
        ClassPathResource ddl = new ClassPathResource("db/postgresql/ai-runtime.sql");
        try (InputStream input = ddl.getInputStream()) {
            String sql = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
            // Refuse a future script that would escape this schema instead of rewriting its meaning.
            assertThat(Pattern.compile("(?im)^\\s*(CREATE|ALTER|DROP)\\s+(DATABASE|SCHEMA)\\b"
                    + "|^\\s*SET\\s+(search_path|SCHEMA)\\b|\\bpublic\\s*\\.").matcher(sql).find())
                    .as("DDL must stay within the connection's private search path").isFalse();
        }
        try (Connection connection = maintenanceConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + safeSchema());
            schemaCreated = true;
            connection.setSchema(schema);
            ScriptUtils.executeSqlScript(connection, new EncodedResource(ddl, StandardCharsets.UTF_8));
        }
        independent = new JdbcTemplate(new DriverManagerDataSource(schemaUrl(), "postgres", ""));
        assertThat(independent.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=?",
                Integer.class, schema)).isEqualTo(5);
        openOwnedResources();
    }

    @AfterEach
    void closeResourcesAndDropOnlyThisTestsSchema() throws Exception {
        try {
            if (resources != null) { resources.close(); }
        } finally {
            if (schemaCreated) {
                try (Connection connection = maintenanceConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + safeSchema() + " CASCADE");
                }
            }
        }
    }

    @Test
    void realCommitAndRollbackControlVisibilityAndAfterCommitActions() {
        AtomicInteger callbacks = new AtomicInteger();
        Long committed = transactions.required(() -> {
            Long id = conversations.createSingle(owner);
            assertThat(rowCount(id)).as("a different connection must not see uncommitted data").isZero();
            transactions.afterCommit(() -> {
                assertThat(rowCount(id)).as("afterCommit observes an actual committed row").isEqualTo(1);
                callbacks.incrementAndGet();
            });
            assertThat(callbacks).hasValue(0);
            return id;
        });
        assertThat(rowCount(committed)).isEqualTo(1);
        assertThat(callbacks).hasValue(1);

        AtomicLong rolledBack = new AtomicLong();
        assertThatThrownBy(() -> transactions.runRequired(() -> {
            Long id = conversations.createSingle(owner);
            rolledBack.set(id);
            transactions.afterCommit(callbacks::incrementAndGet);
            assertThat(rowCount(id)).isZero();
            throw new IllegalStateException("intentional rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("intentional rollback");
        assertThat(rowCount(rolledBack.get())).isZero();
        assertThat(callbacks).as("rollback must not start post-commit work").hasValue(1);
        assertThat(conversations.list(owner, AiChatMode.SINGLE)).extracting(AiConversationView::getId)
                .containsExactly(committed);
    }

    @Test
    void singleChatCrudSurvivesOwnedPoolRecreationAndUsesTerminalCas() {
        AiSingleChatPreparedTurn turn = prepareSingle(null, "persisted question");
        transactions.runRequired(() -> {
            assertThat(chats.terminal(owner, "single", turn.getAssistantMessageId(), turn.getConversationId(),
                    1, "persisted answer", null, "request-1", null)).isTrue();
            assertThat(chats.terminal(owner, "single", turn.getAssistantMessageId(), turn.getConversationId(),
                    3, null, "late failure", null, null)).as("a terminal reply cannot be overwritten").isFalse();
            chats.saveSession(owner, "single", turn.getConversationId(), turn.getAssistantMessageId(), "app-1", "session-1");
            conversations.rename(owner, AiChatMode.SINGLE, turn.getConversationId(), "saved title");
            conversations.pin(owner, AiChatMode.SINGLE, turn.getConversationId(), true);
        });
        HikariDataSource oldPool = (HikariDataSource) resources.source();
        resources.close();
        assertThat(oldPool.isClosed()).isTrue();
        openOwnedResources();
        assertThat(resources.source()).isNotSameAs(oldPool);
        List<AiConversationView> loaded = conversations.list(owner, AiChatMode.SINGLE);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getTitle()).isEqualTo("saved title");
        assertThat(loaded.get(0).isPinned()).isTrue();
        assertThat(loaded.get(0).getPinnedTimeMillis()).isNotNull();
        assertThat(conversations.messages(owner, AiChatMode.SINGLE, turn.getConversationId()))
                .extracting(AiMessageView::getContent).containsExactly("persisted question", "persisted answer");
        AiSingleChatPreparedTurn next = prepareSingle(turn.getConversationId(), "second question");
        assertThat(next.getRemoteSessionId()).isEqualTo("session-1");
        assertThat(next.getHistorySummary()).contains("persisted question", "persisted answer");
        transactions.runRequired(() -> {
            conversations.pin(owner, AiChatMode.SINGLE, turn.getConversationId(), false);
            assertThat(conversations.list(owner, AiChatMode.SINGLE).get(0).getPinnedTimeMillis()).isNull();
            assertThat(conversations.delete(owner, AiChatMode.SINGLE, turn.getConversationId()))
                    .containsExactly(next.getAssistantMessageId());
        });
        assertThat(conversations.list(owner, AiChatMode.SINGLE)).isEmpty();
        assertThat(independent.queryForObject("SELECT status FROM ai_runtime_message WHERE id=?", Integer.class,
                next.getAssistantMessageId())).isEqualTo(2);
        assertThatThrownBy(() -> conversations.messages(owner, AiChatMode.SINGLE, turn.getConversationId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void namespaceTenantAndActorConstrainBothReadsAndWrites() {
        AiInvocationContext otherTenant = actor(NAMESPACE, "tenant-b", "actor-a");
        AiInvocationContext otherActor = actor(NAMESPACE, "tenant-a", "actor-b");
        AiInvocationContext otherNamespace = actor("another-deployment", "tenant-a", "actor-a");
        AiJdbcAccess otherAccess = new AiJdbcAccess(resources.source(), transactions, otherNamespace.getNamespace());
        AiJdbcConversationRepository otherRepository = new AiJdbcConversationRepository(otherAccess,
                new AiJdbcChatRepository(otherAccess, memory()), null);
        Long ownId = transactions.required(() -> conversations.createSingle(owner));
        Long tenantId = transactions.required(() -> conversations.createSingle(otherTenant));
        Long actorId = transactions.required(() -> conversations.createSingle(otherActor));
        Long namespaceId = transactions.required(() -> otherRepository.createSingle(otherNamespace));
        assertThat(conversations.list(owner, AiChatMode.SINGLE)).extracting(AiConversationView::getId).containsExactly(ownId);
        assertThat(conversations.list(otherTenant, AiChatMode.SINGLE)).extracting(AiConversationView::getId).containsExactly(tenantId);
        assertThat(conversations.list(otherActor, AiChatMode.SINGLE)).extracting(AiConversationView::getId).containsExactly(actorId);
        assertThat(otherRepository.list(otherNamespace, AiChatMode.SINGLE)).extracting(AiConversationView::getId).containsExactly(namespaceId);
        for (AiInvocationContext stranger : Arrays.asList(otherTenant, otherActor)) {
            assertThatThrownBy(() -> conversations.messages(stranger, AiChatMode.SINGLE, ownId))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> transactions.runRequired(() -> conversations.rename(stranger, AiChatMode.SINGLE,
                    ownId, "must not change"))).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> otherRepository.messages(otherNamespace, AiChatMode.SINGLE, ownId))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> conversations.list(otherNamespace, AiChatMode.SINGLE))
                .isInstanceOf(IllegalStateException.class).hasMessage("AI 存储身份作用域无效");
        assertThat(independent.queryForObject("SELECT count(*) FROM ai_runtime_conversation", Integer.class)).isEqualTo(4);
    }

    @Test
    void sharingPersistsCompletedContentCountsAccessAndRejectsRevokedOrExpiredCodes() {
        AiSingleChatPreparedTurn turn = prepareSingle(null, "shared question");
        transactions.runRequired(() -> assertThat(chats.terminal(owner, "single", turn.getAssistantMessageId(),
                turn.getConversationId(), 1, "shared answer", null, null, null)).isTrue());
        AiShareLease lease = transactions.required(() -> shares.issue(owner, AiChatMode.SINGLE,
                turn.getConversationId(), 1, randomCode()));
        AiShareLease reused = transactions.required(() -> shares.issue(owner, AiChatMode.SINGLE,
                turn.getConversationId(), 3, randomCode()));
        assertThat(reused.getShareCode()).isEqualTo(lease.getShareCode());
        assertThat(reused.getExpireTimeMillis()).isEqualTo(lease.getExpireTimeMillis());
        prepareSingle(turn.getConversationId(), "next visible question");
        AiSharedConversation shared = transactions.requiresNewReadCommitted(() -> shares.readPublic(AiChatMode.SINGLE,
                lease.getShareCode()));
        assertThat(shared.getMessages()).extracting(AiSharedMessage::getContent)
                .containsExactly("shared question", "shared answer", "next visible question");
        assertThat(independent.queryForObject("SELECT share_access_count FROM ai_runtime_conversation WHERE id=?",
                Long.class, turn.getConversationId())).isEqualTo(1L);
        transactions.runRequired(() -> shares.revoke(owner, AiChatMode.SINGLE, turn.getConversationId()));
        assertThatThrownBy(() -> transactions.requiresNewReadCommitted(() -> shares.readPublic(AiChatMode.SINGLE,
                lease.getShareCode()))).isInstanceOf(AiExecutionException.class);
        AiShareLease expired = transactions.required(() -> shares.issue(owner, AiChatMode.SINGLE,
                turn.getConversationId(), 1, randomCode()));
        independent.update("UPDATE ai_runtime_conversation SET share_expire_at=clock_timestamp()-INTERVAL '1 second' WHERE id=?",
                turn.getConversationId());
        assertThatThrownBy(() -> transactions.requiresNewReadCommitted(() -> shares.readPublic(AiChatMode.SINGLE,
                expired.getShareCode()))).isInstanceOf(AiExecutionException.class);
    }

    @Test
    void groupMemberSnapshotsAndRepliesPersistAcrossUpdatesAndRollback() {
        Long id = transactions.required(() -> groups.create(owner, "group history", Arrays.asList("ARCHITECT", "REVIEWER")));
        assertMembers(id, "ARCHITECT", "REVIEWER");
        assertThatThrownBy(() -> transactions.runRequired(() -> {
            conversations.updateGroupMembers(owner, id, Arrays.asList("REVIEWER", "ARCHITECT"));
            throw new IllegalStateException("intentional member rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("intentional member rollback");
        assertMembers(id, "ARCHITECT", "REVIEWER");
        transactions.runRequired(() -> conversations.updateGroupMembers(owner, id, Arrays.asList("REVIEWER", "ARCHITECT")));
        assertMembers(id, "REVIEWER", "ARCHITECT");
        AiGroupChatPreparedTurn turn = transactions.required(() -> groups.prepare(owner, id, "review request", "group-app", 120));
        assertThat(turn.getMembers()).extracting(AiGroupMemberSnapshot::getCode).containsExactly("REVIEWER", "ARCHITECT");
        transactions.runRequired(() -> {
            assertThat(chats.terminal(owner, "group", turn.getMessageId(), id, 1, "review answer", null, null, null)).isTrue();
            groups.replyMetadata(owner, turn.getMessageId(), new AiGroupReplyRecord("REVIEWER", "Reviewer", "review answer"), 1, "group-request");
        });
        List<AiMessageView> history = conversations.messages(owner, AiChatMode.GROUP, id);
        assertThat(history).extracting(AiMessageView::getContent).containsExactly("review request", "review answer");
        assertThat(history.get(1).getSpeakerCode()).isEqualTo("REVIEWER");
        assertThat(history.get(1).getRoundNo()).isEqualTo(1);
        AiShareLease lease = transactions.required(() -> shares.issue(owner, AiChatMode.GROUP, id, 1, randomCode()));
        AiSharedConversation shared = transactions.requiresNewReadCommitted(() -> shares.readPublic(AiChatMode.GROUP, lease.getShareCode()));
        assertThat(shared.getMembers()).extracting(AiGroupMemberSnapshot::getName).containsExactly("Reviewer", "Architect");
        assertThat(shared.getMessages().get(1).getSpeakerName()).isEqualTo("Reviewer");
        assertThat(shared.getMessages().get(1).getRoundNo()).isEqualTo(1);
    }

    @Test
    void mapperReadsFreshCommittedStateWithinTheSameTransaction() {
        AiSingleChatPreparedTurn turn = prepareSingle(null, "cache check");
        transactions.readOnly(() -> {
            assertThat(chats.status(owner, "single", turn.getAssistantMessageId())).isZero();
            independent.update("UPDATE ai_runtime_message SET status=2 WHERE id=?", turn.getAssistantMessageId());
            // No local mapper write in between: a SESSION cache would incorrectly return 0.
            assertThat(chats.status(owner, "single", turn.getAssistantMessageId())).isEqualTo(2);
            return null;
        });
    }

    @Test
    void mapperResourcesJoinHostTransactionAndRejectDifferentSource() {
        DataSourceTransactionManager manager = new DataSourceTransactionManager(resources.source());
        AiJdbcAccess borrowed = new AiJdbcAccess(resources.source(), manager, NAMESPACE);
        AiJdbcChatRepository repository = new AiJdbcChatRepository(borrowed, memory());
        AiJdbcConversationRepository store = new AiJdbcConversationRepository(borrowed, repository, null);
        AtomicLong id = new AtomicLong();
        new TransactionTemplate(manager).execute(status -> {
            id.set(store.createSingle(owner));
            // A SqlSessionFactory resource is now bound; subsequent same-source work must still participate.
            store.rename(owner, AiChatMode.SINGLE, id.get(), "host transaction");
            borrowed.executor().runRequired(() -> store.pin(owner, AiChatMode.SINGLE, id.get(), true));
            assertThat(rowCount(id.get())).isZero();
            status.setRollbackOnly();
            return null;
        });
        assertThat(rowCount(id.get())).isZero();
        DriverManagerDataSource unrelated = new DriverManagerDataSource(schemaUrl(), "postgres", "");
        new TransactionTemplate(new DataSourceTransactionManager(unrelated)).execute(status -> {
            assertThatThrownBy(() -> borrowed.executor().required(() -> store.createSingle(owner)))
                    .isInstanceOf(IllegalStateException.class);
            return null;
        });
    }

    @Test
    void originAndAuditParticipateInRollbackAndEnforceCallerAndTerminalState() {
        AiCallerOrigin caller = AiCallerOrigin.forIdentifiers("tenant-a", "actor-a", "opaque-client-row", "client-a", "system-a", "test");
        AiJdbcMessageOriginAdapter origin = new AiJdbcMessageOriginAdapter(access, app -> caller);
        AiJdbcExecutionAuditAdapter audit = new AiJdbcExecutionAuditAdapter(access);
        assertThatThrownBy(() -> transactions.runRequired(() -> {
            AiSingleChatPreparedTurn rolledBack = chats.prepare(new AiSingleChatPrepareCommand(owner, null, "rollback", false, "app-1", 120));
            origin.record(owner, AiChatMode.SINGLE, rolledBack.getAssistantMessageId(), "app-1");
            audit.start(owner, AiChatMode.SINGLE, rolledBack.getConversationId(), rolledBack.getAssistantMessageId(), "trace", "app-1");
            throw new IllegalStateException("rollback all tables");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback all tables");
        assertThat(independent.queryForObject("SELECT count(*) FROM ai_runtime_origin", Long.class)).isZero();
        assertThat(independent.queryForObject("SELECT count(*) FROM ai_runtime_execution", Long.class)).isZero();
        assertThat(independent.queryForObject("SELECT count(*) FROM ai_runtime_message", Long.class)).isZero();
        AiSingleChatPreparedTurn turn = transactions.required(() -> {
            AiSingleChatPreparedTurn created = chats.prepare(new AiSingleChatPrepareCommand(owner, null, "audit", false, "app-1", 120));
            origin.record(owner, AiChatMode.SINGLE, created.getAssistantMessageId(), "app-1");
            audit.start(owner, AiChatMode.SINGLE, created.getConversationId(), created.getAssistantMessageId(), "trace", "app-1");
            return created;
        });
        transactions.runRequired(() -> {
            assertThat(origin.verify(owner, caller, AiChatMode.SINGLE, turn.getAssistantMessageId(), Collections.singleton("app-1"))).isEqualTo("app-1");
            AiCallerOrigin stranger = AiCallerOrigin.forIdentifiers("tenant-a", "actor-a", "other-client-row", "client-a", "system-a", "test");
            assertThatThrownBy(() -> origin.verify(owner, stranger, AiChatMode.SINGLE, turn.getAssistantMessageId(), Collections.singleton("app-1")))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> origin.record(actor(NAMESPACE, "other-tenant", "actor-a"), AiChatMode.SINGLE, turn.getAssistantMessageId(), "app-1"))
                    .isInstanceOf(IllegalStateException.class);
        });
        audit.incrementRetry(owner, AiChatMode.SINGLE, turn.getAssistantMessageId());
        audit.fail(actor(NAMESPACE, "other-tenant", "actor-a"), AiChatMode.SINGLE, turn.getAssistantMessageId(), 1, "WRONG_OWNER");
        audit.complete(owner, AiChatMode.SINGLE, turn.getAssistantMessageId(), new AiExecutionMetrics("request", "model", null, 7, null, null, 25));
        audit.fail(owner, AiChatMode.SINGLE, turn.getAssistantMessageId(), 999, "LATE_FAILURE");
        Map<String, Object> saved = independent.queryForMap("SELECT * FROM ai_runtime_execution WHERE message_id=?", turn.getAssistantMessageId());
        assertThat(((Number) saved.get("status")).intValue()).isEqualTo(1);
        assertThat(saved.get("retry_count")).isEqualTo(1);
        assertThat(saved.get("input_tokens")).isNull();
        assertThat(saved.get("first_token_ms")).isNull();
        assertThat(saved.get("error_code")).isNull();
        assertThat(saved.get("total_duration_ms")).isEqualTo(25L);
    }

    @Test
    void staleCleanupAndDeleteCloseAuditWithoutOverwritingEarlierTerminals() {
        AiJdbcExecutionAuditAdapter audit = new AiJdbcExecutionAuditAdapter(access);
        AiSingleChatPreparedTurn stale = prepareSingle(null, "old request");
        transactions.runRequired(() -> audit.start(owner, AiChatMode.SINGLE, stale.getConversationId(), stale.getAssistantMessageId(), "trace", "app-1"));
        independent.update("UPDATE ai_runtime_message SET created_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE id=?", stale.getAssistantMessageId());
        independent.update("UPDATE ai_runtime_execution SET created_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE message_id=?", stale.getAssistantMessageId());
        AiSingleChatPreparedTurn next = prepareSingle(stale.getConversationId(), "new request");
        assertThat(chats.status(owner, "single", stale.getAssistantMessageId())).isEqualTo(3);
        assertThat(independent.queryForObject("SELECT error_code FROM ai_runtime_execution WHERE message_id=?", String.class,
                stale.getAssistantMessageId())).isEqualTo("STALE_TIMEOUT");
        transactions.runRequired(() -> {
            audit.start(owner, AiChatMode.SINGLE, next.getConversationId(), next.getAssistantMessageId(), "trace", "app-1");
            conversations.delete(owner, AiChatMode.SINGLE, next.getConversationId());
        });
        assertThat(independent.queryForObject("SELECT status FROM ai_runtime_execution WHERE message_id=?", Integer.class,
                next.getAssistantMessageId())).isEqualTo(2);
        assertThat(independent.queryForObject("SELECT error_code FROM ai_runtime_execution WHERE message_id=?", String.class,
                stale.getAssistantMessageId())).isEqualTo("STALE_TIMEOUT");
    }

    @Test
    void concurrentPrepareAllowsOnlyOneGeneratingTurn() throws Exception {
        Long id = transactions.required(() -> conversations.createSingle(owner));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("start timeout"); }
            try { prepareSingle(id, "concurrent question"); return true; }
            catch (IllegalStateException busy) {
                assertThat(busy).hasMessage("当前对话正在生成回复，请稍候");
                return false;
            }
        };
        try {
            Future<Boolean> first = workers.submit(attempt); Future<Boolean> second = workers.submit(attempt);
            start.countDown();
            assertThat(Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(independent.queryForObject("SELECT count(*) FROM ai_runtime_message WHERE role='assistant' AND status=0", Long.class)).isEqualTo(1L);
            assertThat(independent.queryForObject("SELECT count(*) FROM ai_runtime_message WHERE role='user'", Long.class)).isEqualTo(1L);
        } finally { workers.shutdownNow(); }
    }

    @Test
    void returningShareWriteAndAccessCounterRollBackWithTheirTransaction() {
        Long id = transactions.required(() -> conversations.createSingle(owner));
        assertThatThrownBy(() -> transactions.runRequired(() -> {
            shares.issue(owner, AiChatMode.SINGLE, id, 1, randomCode());
            throw new IllegalStateException("share rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("share rollback");
        assertThat(independent.queryForObject("SELECT share_code FROM ai_runtime_conversation WHERE id=?", String.class, id)).isNull();
        AiShareLease lease = transactions.required(() -> shares.issue(owner, AiChatMode.SINGLE, id, 1, randomCode()));
        assertThatThrownBy(() -> transactions.runRequired(() -> {
            shares.readPublic(AiChatMode.SINGLE, lease.getShareCode());
            throw new IllegalStateException("counter rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("counter rollback");
        assertThat(independent.queryForObject("SELECT share_access_count FROM ai_runtime_conversation WHERE id=?", Long.class, id)).isZero();
    }

    @Test
    void shareRevokedWhileContentLoadsCannotEscapeFinalValidityCheck() throws Exception {
        Long id = transactions.required(() -> conversations.createSingle(owner));
        AiShareLease lease = transactions.required(() -> shares.issue(owner, AiChatMode.SINGLE, id, 1, randomCode()));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (Connection blocker = DriverManager.getConnection(schemaUrl(), "postgres", ""); Statement statement = blocker.createStatement()) {
            blocker.setAutoCommit(false);
            statement.execute("LOCK TABLE ai_runtime_message IN ACCESS EXCLUSIVE MODE");
            Future<AiSharedConversation> read = worker.submit(() -> transactions.requiresNewReadCommitted(
                    () -> shares.readPublic(AiChatMode.SINGLE, lease.getShareCode())));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean contentReadBlocked = false;
            while (System.nanoTime() < deadline) {
                contentReadBlocked = independent.queryForObject(
                        "SELECT count(*) FROM pg_locks WHERE relation=CAST(? AS regclass) AND NOT granted", Long.class,
                        safeSchema() + ".ai_runtime_message") > 0;
                if (contentReadBlocked) { break; }
                Thread.sleep(20);
            }
            assertThat(contentReadBlocked).as("public read reached content query after accepting the initial share code").isTrue();
            transactions.runRequired(() -> shares.revoke(owner, AiChatMode.SINGLE, id));
            blocker.commit();
            assertThatThrownBy(() -> read.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(AiExecutionException.class);
            assertThat(independent.queryForObject("SELECT share_access_count FROM ai_runtime_conversation WHERE id=?", Long.class, id)).isZero();
        } finally { worker.shutdownNow(); }
    }

    private void openOwnedResources() {
        AiJdbcResourceProperties properties = new AiJdbcResourceProperties();
        properties.setMode(AiJdbcResourceMode.ISOLATED);
        properties.setJdbcUrl(schemaUrl());
        properties.setUsername("postgres");
        properties.setPassword("");
        properties.setMaximumPoolSize(3);
        properties.setConnectionTimeoutMs(3000);
        resources = AiJdbcResources.isolated(properties);
        transactions = new AiTransactionExecutor(resources.manager(), AiTransactionMode.ISOLATED);
        access = new AiJdbcAccess(resources.source(), transactions, NAMESPACE);
        chats = new AiJdbcChatRepository(access, memory());
        AiGroupAgentCatalogPort catalog = (context, codes) -> {
            List<AiGroupMemberSnapshot> result = new ArrayList<>();
            for (String code : codes) {
                if ("ARCHITECT".equals(code)) { result.add(new AiGroupMemberSnapshot(code, "Architect", "Design the system")); }
                else if ("REVIEWER".equals(code)) { result.add(new AiGroupMemberSnapshot(code, "Reviewer", "Review the design")); }
                else { throw new IllegalStateException("Unknown integration fixture member"); }
            }
            return result;
        };
        conversations = new AiJdbcConversationRepository(access, chats, catalog);
        shares = new AiJdbcShareRepository(access, chats);
        groups = new AiJdbcGroupRepository(access, chats, catalog);
    }

    private AiSingleChatPreparedTurn prepareSingle(Long conversationId, String content) {
        return transactions.required(() -> chats.prepare(new AiSingleChatPrepareCommand(owner, conversationId,
                content, false, "app-1", 120)));
    }

    private void assertMembers(Long id, String... codes) {
        List<AiConversationView> found = conversations.list(owner, AiChatMode.GROUP);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(id);
        assertThat(found.get(0).getMemberCodes()).containsExactly(codes);
        assertThat(found.get(0).getMembers()).extracting(AiGroupMemberSnapshot::getCode).containsExactly(codes);
        assertThat(found.get(0).getMembers()).allSatisfy(member -> {
            assertThat(member.getName()).isNotBlank();
            assertThat(member.getRole()).isNotBlank();
        });
    }

    private int rowCount(long id) {
        return independent.queryForObject("SELECT count(*) FROM ai_runtime_conversation WHERE id=?", Integer.class, id);
    }

    private Connection maintenanceConnection() throws Exception { return DriverManager.getConnection(baseUrl, "postgres", ""); }
    private String schemaUrl() { return baseUrl + "?currentSchema=" + safeSchema(); }
    private String safeSchema() {
        if (schema == null || !OWN_SCHEMA.matcher(schema).matches()) { throw new IllegalStateException("Invalid private integration schema"); }
        return schema;
    }
    private static String randomCode() { return UUID.randomUUID().toString().replace("-", ""); }
    private static AiConversationMemoryService memory() { return new AiConversationMemoryService(new BailianProperties()); }
    private static AiInvocationContext actor(String namespace, String tenant, String actor) {
        return new AiInvocationContext(namespace, tenant, actor, "postgres-integration-invocation");
    }
}
