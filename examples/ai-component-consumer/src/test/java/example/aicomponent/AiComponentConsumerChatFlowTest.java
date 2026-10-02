package example.aicomponent;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcExecutionScopeAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import io.github.yoyocw.aichatkit.ai.starter.host.*;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebStreamResponseFactory;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.model.*;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiExecutionEventSink;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionMetrics;
import io.github.yoyocw.aichatkit.ai.engine.execution.AiSseEventEncoder;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import java.io.OutputStream;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatBusinessPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextStatus;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
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
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
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
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(group.send(conversation, "test question", ACTOR));
            long message = assistantId(db, conversation);
            assertTerminal(db, message, 0);
            AtomicInteger memberObservations = new AtomicInteger();
            ByteArrayOutputStream output = new ByteArrayOutputStream() {
                @Override public void flush() throws IOException {
                    super.flush();
                    String frames = new String(toByteArray(), StandardCharsets.UTF_8);
                    if (frames.contains("event:speaker-start\n")) {
                        assertThat(db.queryForList("SELECT status FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                                Integer.class, conversation)).containsExactly(1, 1, 1);
                        assertThat(db.queryForList("SELECT content FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                                String.class, conversation)).containsExactly("design answer", "review answer", "final summary");
                        memberObservations.incrementAndGet();
                    }
                }
            };
            body.writeTo(output);
            String sse = new String(output.toByteArray(), StandardCharsets.UTF_8);
            assertThat(memberObservations.get()).isPositive();
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
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(single.send(request(conversation), ACTOR));
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

    @Test
    void nullBusinessCredentialsCannotBypassApplicationPermission() throws Exception {
        withHost((context, db) -> {
            context.getBean(FixedIdentity.class).allowed.remove("test:ai:invoke");
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            assertThatThrownBy(() -> context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR))
                    .isInstanceOf(RuntimeException.class);
            assertThat(context.getBean(ModelFixture.class).calls.get()).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM ai_runtime_message WHERE conversation_id=?", Integer.class, conversation)).isZero();
        }, NullCredentialsBusiness.class);
    }

    @Test
    void outerRequiredTransactionRejectsConsumptionBeforeCommitAndAfterRollback() throws Exception {
        withHost((context, db) -> {
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            AtomicReference<StreamingResponseBody> body = new AtomicReference<>();
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                assertThatThrownBy(() -> context.getBean(AiTransactionExecutor.class).required(() -> {
                    body.set(context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR)));
                    try {
                        worker.submit(() -> assertThatThrownBy(() -> write(body.get())).isInstanceOf(IllegalStateException.class))
                                .get(10, TimeUnit.SECONDS);
                    } catch (Exception ex) { throw new AssertionError("pre-commit consumption assertion", ex); }
                    assertThat(context.getBean(ModelFixture.class).calls.get()).isZero();
                    throw new TestRollbackException();
                })).isInstanceOf(TestRollbackException.class);
                assertThatThrownBy(() -> write(body.get())).isInstanceOf(IllegalStateException.class);
                assertThat(context.getBean(ModelFixture.class).calls.get()).isZero();
                assertThat(db.queryForObject("SELECT count(*) FROM ai_runtime_message WHERE conversation_id=?", Integer.class, conversation)).isZero();
            } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        });
    }

    @Test
    void committedStreamingResponseCanOnlyBeConsumedOnce() throws Exception {
        withHost((context, db) -> {
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
            long message = assistantId(db, conversation);
            assertDone(write(body), message, 1);
            assertThatThrownBy(() -> write(body)).isInstanceOf(IllegalStateException.class);
            assertThat(context.getBean(ModelFixture.class).calls.get()).isEqualTo(1);
            assertTerminal(db, message, 1);
        });
    }

    @Test
    void sessionExpiryAfterDeltaNeverRetriesAndPreservesPartialAnswer() throws Exception {
        withHost((context, db) -> {
            ModelFixture model = context.getBean(ModelFixture.class);
            model.expiryCalls = 1; model.emitBeforeExpiry = true;
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
            long message = assistantId(db, conversation);
            String sse = write(body);
            assertDone(sse, message, 3);
            assertThat(model.calls.get()).isEqualTo(1);
            assertThat(events(sse, "context-reset")).isEmpty();
            assertThat(db.queryForObject("SELECT content FROM ai_runtime_message WHERE id=?", String.class, message)).isEqualTo("partial answer");
            assertTerminal(db, message, 3);
        });
    }

    @Test
    void sessionExpiryBeforeAnyDeltaRetriesAtMostOnce() throws Exception {
        withHost((context, db) -> {
            ModelFixture model = context.getBean(ModelFixture.class);
            model.expiryCalls = 2;
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
            long message = assistantId(db, conversation);
            String sse = write(body);
            assertDone(sse, message, 3);
            assertThat(model.calls.get()).isEqualTo(2);
            assertThat(events(sse, "context-reset")).hasSize(1);
            assertThat(events(sse, "delta")).isEmpty();
            assertTerminal(db, message, 3);
        });
    }

    @Test
    void completionAuditFailureCannotChangeCommittedAnswerOrDone() throws Exception {
        withHost((context, db) -> {
            db.execute("CREATE FUNCTION reject_test_audit_complete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                    + "IF NEW.status=1 AND OLD.status=0 THEN RAISE EXCEPTION 'synthetic audit failure'; END IF; RETURN NEW; END $$");
            db.execute("CREATE TRIGGER reject_test_audit_complete BEFORE UPDATE ON ai_runtime_execution "
                    + "FOR EACH ROW EXECUTE FUNCTION reject_test_audit_complete()");
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
            long message = assistantId(db, conversation);
            String sse = write(body);
            assertDone(sse, message, 1);
            assertThat(events(sse, "error")).isEmpty();
            assertThat(db.queryForObject("SELECT status FROM ai_runtime_message WHERE id=?", Integer.class, message)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT content FROM ai_runtime_message WHERE id=?", String.class, message)).isEqualTo("single answer");
            assertThat(db.queryForObject("SELECT status FROM ai_runtime_execution WHERE message_id=?", Integer.class, message)).isZero();
        });
    }
    @Test
    void neutralModelAndRetainedEventSnapshotsCompleteBothModesWithoutBailianOrGlobalAdvisor() throws Exception {
        withHost(NeutralHost.class, (context, db) -> {
            assertThat(context).doesNotHaveBean(BailianClient.class).doesNotHaveBean(BailianProperties.class);
            assertThat(context.containsBean("org.springframework.transaction.config.internalTransactionAdvisor")).isFalse();
            FixedIdentity identity = context.getBean(FixedIdentity.class);
            NeutralModelFixture model = context.getBean(NeutralModelFixture.class);
            long singleConversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            identity.applicationChecks.set(0);
            AiPreparedExecution single = context.getBean(AiHostSingleChatService.class).prepare(request(singleConversation), ACTOR);
            long singleMessage = assistantId(db, singleConversation);
            assertTerminal(db, singleMessage, 0);
            assertThat(identity.applicationChecks.get()).isEqualTo(1);
            assertThat(model.modes).isEmpty();
            RetainedEvents singleEvents = new RetainedEvents();
            single.consume(singleEvents);
            assertThat(identity.applicationChecks.get()).isEqualTo(1);
            assertTerminal(db, singleMessage, 1);
            assertThat(singleEvents.names).containsSubsequence("start", "delta", "done");
            assertThat(singleEvents.data("delta").get("content")).isEqualTo("neutral single answer");
            assertThat(singleEvents.data("done").keySet()).containsExactly("messageId", "status", "requestId");
            assertDone(singleEvents.sse(), singleMessage, 1);
            assertThat(singleEvents.sse()).contains("event:done\ndata:{\"messageId\":" + singleMessage
                    + ",\"status\":1,\"requestId\":\"neutral-request\"}\n\n");
            assertThatThrownBy(() -> single.consume(singleEvents)).isInstanceOf(IllegalStateException.class);

            AiHostGroupChatService groupService = context.getBean(AiHostGroupChatService.class);
            long groupConversation = groupService.create("neutral group", MEMBERS, ACTOR);
            identity.applicationChecks.set(0);
            AiPreparedExecution group = groupService.prepare(groupConversation, "test question", ACTOR);
            long groupMessage = assistantId(db, groupConversation);
            assertTerminal(db, groupMessage, 0);
            assertThat(identity.applicationChecks.get()).isEqualTo(1);
            RetainedEvents groupEvents = new RetainedEvents();
            group.consume((event, data) -> {
                if ("speaker-start".equals(event)) {
                    assertThat(db.queryForList("SELECT status FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                            Integer.class, groupConversation)).containsExactly(1, 1, 1);
                }
                groupEvents.accept(event, data);
            });
            assertThat(identity.applicationChecks.get()).isEqualTo(1);
            assertTerminal(db, groupMessage, 1);
            assertThat(db.queryForList("SELECT content FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                    String.class, groupConversation)).containsExactly("neutral design", "neutral review", "neutral summary");
            assertThat(events(groupEvents.sse(), "speaker-start")).extracting(node -> node.path("speakerCode").asText())
                    .containsExactly("ARCHITECT", "REVIEWER", "ORCHESTRATOR");
            assertThat(events(groupEvents.sse(), "delta")).extracting(node -> node.path("content").asText())
                    .containsExactly("neutral design", "neutral review", "neutral summary");
            assertDone(groupEvents.sse(), groupMessage, 1);
            assertThat(model.modes).containsExactly(AiChatMode.SINGLE, AiChatMode.GROUP);
            singleEvents.assertRetainedSnapshots();
            groupEvents.assertRetainedSnapshots();
        });
    }

    private static final class RetainedEvents implements AiExecutionEventSink {
        final List<String> names = new ArrayList<>();
        final List<Map<String, Object>> snapshots = new ArrayList<>();
        final List<String> serializedAtDelivery = new ArrayList<>();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final AiSseEventEncoder encoder = new AiSseEventEncoder(output);
        public void accept(String event, Map<String, Object> data) {
            names.add(event);
            // Retain exactly the delivered object: later events must not overwrite its fields or nested data.
            snapshots.add(data);
            try { serializedAtDelivery.add(new ObjectMapper().writeValueAsString(data)); }
            catch (IOException ex) { throw new AssertionError(ex); }
            encoder.accept(event, data);
        }
        Map<String, Object> data(String event) { return snapshots.get(names.indexOf(event)); }
        String sse() { return new String(output.toByteArray(), StandardCharsets.UTF_8); }
        void assertRetainedSnapshots() throws IOException {
            for (int i = 0; i < snapshots.size(); i++) {
                assertThat(new ObjectMapper().writeValueAsString(snapshots.get(i))).isEqualTo(serializedAtDelivery.get(i));
                for (int j = 0; j < i; j++) { assertThat(snapshots.get(i)).isNotSameAs(snapshots.get(j)); }
            }
        }
    }
    @Test
    void existingWriterBeanMayAppendFieldsInPlaceAndStillCompleteSingleChat() throws Exception {
        withHost((context, db) -> {
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
            long message = assistantId(db, conversation);
            String sse = write(body);
            assertDone(sse, message, 1);
            assertTerminal(db, message, 1);
            assertThat(events(sse, "delta")).extracting(node -> node.path("content").asText()).contains("single answer");
            assertThat(events(sse, "delta")).allSatisfy(node -> assertThat(node.path("customWriter").asBoolean()).isTrue());
            assertThat(events(sse, "done").get(0).path("customWriter").asBoolean()).isTrue();
            assertThat(context.getBean(AppendingEventWriter.class).callbacks.get()).isPositive();
        }, ExistingWriterHost.class);
    }
    @Test
    void groupSessionExpiryRetriesOnceAndEmitsMembersOnlyAfterSuccessfulCompletion() throws Exception {
        for (int expiryCount : Arrays.asList(1, 2)) {
            withHost((context, db) -> {
                ModelFixture model = context.getBean(ModelFixture.class);
                model.groupExpiryCalls = expiryCount;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                model.onInvoke = message -> {
                    String pending = new String(output.toByteArray(), StandardCharsets.UTF_8);
                    assertThat(pending).doesNotContain("event:speaker-start", "event:speaker-done", "event:delta");
                    assertTerminal(db, message, 0);
                };
                AiHostGroupChatService group = context.getBean(AiHostGroupChatService.class);
                long conversation = group.create("retry group", MEMBERS, ACTOR);
                StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(group.send(conversation, "test question", ACTOR));
                long message = assistantId(db, conversation);
                body.writeTo(output);
                String sse = new String(output.toByteArray(), StandardCharsets.UTF_8);
                assertThat(model.calls.get()).isEqualTo(2);
                assertThat(events(sse, "context-reset")).hasSize(1);
                assertThat(db.queryForObject("SELECT retry_count FROM ai_runtime_execution WHERE message_id=?", Integer.class, message)).isEqualTo(1);
                int terminal = expiryCount == 1 ? 1 : 3;
                assertDone(sse, message, terminal);
                assertTerminal(db, message, terminal);
                if (expiryCount == 1) {
                    assertThat(events(sse, "speaker-done")).hasSize(3);
                    assertThat(events(sse, "delta")).extracting(node -> node.path("content").asText())
                            .containsExactly("design answer", "review answer", "final summary");
                } else {
                    assertThat(events(sse, "speaker-start")).isEmpty();
                    assertThat(events(sse, "delta")).isEmpty();
                    assertThat(db.queryForObject("SELECT count(*) FROM ai_runtime_message WHERE conversation_id=? AND role='assistant'", Integer.class, conversation)).isEqualTo(1);
                }
            });
        }
    }

    @Test
    void groupCompletionAuditFailurePreservesAtomicRepliesAndSuccessfulOutput() throws Exception {
        withHost((context, db) -> {
            rejectCompletionAudit(db);
            AiHostGroupChatService group = context.getBean(AiHostGroupChatService.class);
            long conversation = group.create("audit group", MEMBERS, ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(group.send(conversation, "test question", ACTOR));
            long message = assistantId(db, conversation);
            String sse = write(body);
            assertDone(sse, message, 1);
            assertThat(events(sse, "error")).isEmpty();
            assertThat(events(sse, "speaker-done")).hasSize(3);
            assertThat(events(sse, "delta")).extracting(node -> node.path("content").asText())
                    .containsExactly("design answer", "review answer", "final summary");
            assertThat(db.queryForList("SELECT status FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                    Integer.class, conversation)).containsExactly(1, 1, 1);
            assertThat(db.queryForList("SELECT content FROM ai_runtime_message WHERE conversation_id=? AND role='assistant' ORDER BY id",
                    String.class, conversation)).containsExactly("design answer", "review answer", "final summary");
            assertThat(db.queryForObject("SELECT status FROM ai_runtime_execution WHERE message_id=?", Integer.class, message)).isZero();
        });
    }

    @Test
    void disconnectDuringDeltaPersistsFailureAndRestoresWorkerHostContext() throws Exception {
        withHost((context, db) -> {
            RestoringExecutionScope scope = context.getBean(RestoringExecutionScope.class);
            context.getBean(ModelFixture.class).onInvoke = message -> assertThat(scope.actor.get()).isEqualTo(ACTOR);
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
            long message = assistantId(db, conversation);
            AtomicInteger rejectedDeltas = new AtomicInteger();
            ByteArrayOutputStream acceptedOutput = new ByteArrayOutputStream();
            OutputStream disconnected = new OutputStream() {
                private boolean connectionLost;
                public void write(int value) throws IOException {
                    if (connectionLost) { throw new IOException("synthetic disconnected client"); }
                    acceptedOutput.write(value);
                }
                @Override public void write(byte[] data, int offset, int length) throws IOException {
                    String frame = new String(data, offset, length, StandardCharsets.UTF_8);
                    if (!connectionLost && frame.startsWith("event:delta\n")) {
                        rejectedDeltas.incrementAndGet();
                        connectionLost = true;
                    }
                    if (connectionLost) { throw new IOException("synthetic disconnected client"); }
                    acceptedOutput.write(data, offset, length);
                }
            };
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                worker.submit(() -> {
                    scope.actor.set("previous-worker-context");
                    try {
                        body.writeTo(disconnected);
                        assertThat(scope.actor.get()).isEqualTo("previous-worker-context");
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
                    } finally { scope.actor.remove(); }
                    return null;
                }).get(10, TimeUnit.SECONDS);
                assertThat(worker.submit(() -> scope.actor.get()).get(10, TimeUnit.SECONDS)).isNull();
            } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
            assertThat(rejectedDeltas.get()).isEqualTo(1);
            assertThat(scope.restorations.get()).isEqualTo(1);
            assertTerminal(db, message, 3);
            assertThat(db.queryForObject("SELECT content FROM ai_runtime_message WHERE id=?", String.class, message)).isEqualTo("single answer");
            assertThat(db.queryForObject("SELECT error_message FROM ai_runtime_message WHERE id=?", String.class, message))
                    .doesNotContain("synthetic disconnected client");
        }, RestoringScopeHost.class);
    }

    @Test
    void controlledModelTimeoutProducesSafeFailedSqlAndSseWithoutRetry() throws Exception {
        withHost((context, db) -> {
            ModelFixture model = context.getBean(ModelFixture.class);
            model.timeout = true;
            long conversation = context.getBean(AiHostConversationManagementService.class).createSingle(ACTOR);
            StreamingResponseBody body = context.getBean(AiWebStreamResponseFactory.class).stream(context.getBean(AiHostSingleChatService.class).send(request(conversation), ACTOR));
            long message = assistantId(db, conversation);
            String sse = write(body);
            assertDone(sse, message, 3);
            assertTerminal(db, message, 3);
            assertThat(model.calls.get()).isEqualTo(1);
            assertThat(events(sse, "delta")).isEmpty();
            assertThat(events(sse, "context-reset")).isEmpty();
            assertThat(events(sse, "error")).hasSize(1);
            JsonNode error = events(sse, "error").get(0);
            assertThat(error.path("code").asInt()).isEqualTo(1_509_000_026);
            assertThat(error.path("retryable").asBoolean()).isTrue();
            assertThat(sse).doesNotContain("synthetic-private-upstream-body");
            assertThat(db.queryForObject("SELECT error_code FROM ai_runtime_execution WHERE message_id=?", String.class, message))
                    .isEqualTo("1509000026");
            assertThat(db.queryForObject("SELECT error_message FROM ai_runtime_message WHERE id=?", String.class, message))
                    .isEqualTo(error.path("message").asText()).doesNotContain("synthetic-private-upstream-body");
        });
    }

    private static void rejectCompletionAudit(JdbcTemplate db) {
        db.execute("CREATE FUNCTION reject_test_audit_complete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.status=1 AND OLD.status=0 THEN RAISE EXCEPTION 'synthetic audit failure'; END IF; RETURN NEW; END $$");
        db.execute("CREATE TRIGGER reject_test_audit_complete BEFORE UPDATE ON ai_runtime_execution "
                + "FOR EACH ROW EXECUTE FUNCTION reject_test_audit_complete()");
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

    private void withHost(Flow flow, Class<?>... extraConfiguration) throws Exception {
        withHost(Host.class, flow, extraConfiguration);
    }
    private void withHost(Class<?> hostConfiguration, Flow flow, Class<?>... extraConfiguration) throws Exception {
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
                new WebApplicationContextRunner().withUserConfiguration(hostConfiguration).withUserConfiguration(extraConfiguration).withPropertyValues(
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
    private static final class TestRollbackException extends RuntimeException { }
    private interface Flow { void run(AssertableWebApplicationContext context, JdbcTemplate db) throws Exception; }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableAiChatKit
    static class Host {
        @Bean FixedIdentity identity() { return new FixedIdentity(); }
        @Bean AiOriginContextPort origin() { return app -> null; }
        @Bean ModelFixture model() { return new ModelFixture(); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableAiChatKit
    static class NeutralHost {
        @Bean FixedIdentity identity() { return new FixedIdentity(); }
        @Bean AiOriginContextPort origin() { return app -> null; }
        @Bean NeutralModelFixture neutralModel() { return new NeutralModelFixture(); }
    }
    static class NeutralModelFixture implements AiModelClient {
        final List<AiChatMode> modes = new ArrayList<>();
        public boolean isConfigured(AiChatMode mode, String app) { return APP.equals(app); }
        public AiModelResult stream(AiModelRequest request, Consumer<AiModelEvent> events, BooleanSupplier generating) {
            assertThat(generating.getAsBoolean()).isTrue();
            modes.add(request.getMode());
            AiExecutionMetrics metrics = new AiExecutionMetrics("neutral-request", "neutral-model", 5, 8, 0, 1L, 1L);
            if (request.getMode() == AiChatMode.SINGLE) {
                events.accept(AiModelEvent.delta("neutral single answer"));
                return new AiModelResult("neutral single answer", "neutral-request", "neutral-session", "stop", null,
                        metrics, Collections.emptyList(), null);
            }
            assertThat(request.getMembers()).extracting("code").containsExactly("ARCHITECT", "REVIEWER");
            return new AiModelResult("neutral summary", "neutral-request", "neutral-session", "stop", null, metrics,
                    Arrays.asList(new AiModelGroupReply("ARCHITECT", "neutral design"),
                            new AiModelGroupReply("REVIEWER", "neutral review"),
                            new AiModelGroupReply("ORCHESTRATOR", "neutral summary")), null);
        }
        public void cancel(AiChatMode mode, Long id) { }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExistingWriterHost {
        @Bean AppendingEventWriter appendingEventWriter() { return new AppendingEventWriter(); }
    }
    static class AppendingEventWriter extends AiChatStreamEventWriter {
        final AtomicInteger callbacks = new AtomicInteger();
        @Override public void write(OutputStream output, String event, Map<String, Object> data) {
            callbacks.incrementAndGet();
            data.put("customWriter", true);
            super.write(output, event, data);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RestoringScopeHost {
        @Bean RestoringExecutionScope restoringScope(AiJdbcAccess access) { return new RestoringExecutionScope(access); }
    }
    static class RestoringExecutionScope implements AiHostExecutionScopePort {
        final ThreadLocal<String> actor = new ThreadLocal<>();
        final AtomicInteger restorations = new AtomicInteger();
        final AiJdbcExecutionScopeAdapter delegate;
        RestoringExecutionScope(AiJdbcAccess access) { delegate = new AiJdbcExecutionScopeAdapter(access); }
        public void execute(AiInvocationContext context, Runnable task) {
            String previous = actor.get();
            actor.set(context.getActorId());
            try { delegate.execute(context, task); }
            finally {
                if (previous == null) { actor.remove(); } else { actor.set(previous); }
                restorations.incrementAndGet();
            }
        }
        public void executeStateRead(AiInvocationContext context, Runnable task) { delegate.executeStateRead(context, task); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class NullCredentialsBusiness {
        @Bean AiSingleChatBusinessPort nullCredentials() {
            return new AiSingleChatBusinessPort() {
                public AiBusinessSnapshot prepareBusinessContext(boolean enabled, String question, AiInvocationContext context) {
                    return new AiBusinessSnapshot(AiBusinessContextStatus.NOT_REQUESTED, null, null);
                }
                public String getMcpAuthorization(AiApplicationConfig config, AiInvocationContext context) { return null; }
            };
        }
    }
    static class FixedIdentity implements AiInvocationContextPort, AiHostSessionPort, AiHostPermissionPort {
        final AtomicInteger applicationChecks = new AtomicInteger();
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
        public boolean hasPermission(AiInvocationContext context, String permission) {
            currentSession(context);
            if ("test:ai:invoke".equals(permission)) { applicationChecks.incrementAndGet(); }
            return allowed.contains(permission);
        }
        public boolean isPlatformAdministrator(AiInvocationContext context) { return false; }
    }
    static class ModelFixture extends BailianClient {
        final AtomicInteger calls = new AtomicInteger(), cancellations = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean block, emitBeforeExpiry, timeout;
        volatile int expiryCalls, groupExpiryCalls;
        LongConsumer onInvoke = id -> { }, onCancel = id -> { };
        ModelFixture() { super(new BailianProperties()); }
        @Override public boolean isConfigured(String app) { return APP.equals(app); }
        @Override public boolean isGroupWorkflowConfigured(String app) { return APP.equals(app); }
        @Override public BailianStreamResult stream(Long id, String app, String prompt, String session,
                Map<String, Object> params, Consumer<BailianStreamEvent> consumer, BooleanSupplier generating) throws IOException {
            int invocation = calls.incrementAndGet(); onInvoke.accept(id);
            if (timeout) { throw new java.net.SocketTimeoutException("synthetic-private-upstream-body"); }
            if (invocation <= expiryCalls) {
                if (emitBeforeExpiry) { consumer.accept(BailianStreamEvent.delta("partial answer")); }
                throw new BailianSessionExpiredException("synthetic expired session");
            }
            consumer.accept(BailianStreamEvent.delta("single answer")); entered.countDown();
            if (block) {
                try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IOException("Test cancellation timed out"); } }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException(ex); }
                throw new IOException("Test model cancelled");
            }
            return result("single answer");
        }
        @Override public BailianStreamResult streamGroupWorkflow(Long id, String app, String prompt, String session,
                Map<String, Object> params, BooleanSupplier generating) throws IOException {
            int invocation = calls.incrementAndGet(); onInvoke.accept(id);
            if (invocation <= groupExpiryCalls) { throw new BailianSessionExpiredException("synthetic expired group session"); }
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
