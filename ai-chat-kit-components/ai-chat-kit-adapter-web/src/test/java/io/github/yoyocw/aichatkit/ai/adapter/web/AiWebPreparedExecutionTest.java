package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterMarkerConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivationConfiguration;
import io.github.yoyocw.aichatkit.ai.starter.host.*;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.*;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.*;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Proves the actual optional Web configuration wraps neutral host APIs after synchronous preparation. */
class AiWebPreparedExecutionTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(Host.class)
            .withConfiguration(AutoConfigurations.of(AiWebExecutionAutoConfiguration.class, AiWebAutoConfiguration.class))
            .withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=true",
                    "ai-chat-kit.ai.starter.namespace=test", "ai-chat-kit.ai.starter.modes[0]=SINGLE",
                    "ai-chat-kit.ai.starter.modes[1]=GROUP");

    @Test void singleAndGroupControllersPrepareSynchronouslyAndStreamThroughWebFactory() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AiWebStreamResponseFactory.class)
                    .hasSingleBean(AiChatExecutionService.class).hasSingleBean(AiGroupChatExecutionService.class)
                    .hasSingleBean(AiGroupChatStreamService.class);
            AtomicInteger consumed = new AtomicInteger();
            AiPreparedExecution prepared = sink -> {
                consumed.incrementAndGet();
                sink.accept("delta", Collections.singletonMap("content", "forest"));
            };
            AiSingleChatExecutor single = context.getBean(AiSingleChatExecutor.class);
            AiGroupChatExecutor group = context.getBean(AiGroupChatExecutor.class);
            when(single.sendMessageForContext(any(AiSingleChatRequest.class), any(AiInvocationContext.class)))
                    .thenReturn(prepared);
            when(group.sendMessageForContext(eq(8L), eq("question"), any(AiInvocationContext.class)))
                    .thenReturn(prepared);
            AiWebSingleSendRequest singleRequest = new AiWebSingleSendRequest();
            singleRequest.setContent("question");
            AiWebGroupSendRequest groupRequest = new AiWebGroupSendRequest();
            groupRequest.setConversationId(8L); groupRequest.setContent("question");
            StreamingResponseBody singleResponse = context.getBean(AiWebSingleController.class).send(singleRequest);
            StreamingResponseBody groupResponse = context.getBean(AiWebGroupController.class).send(groupRequest);
            verify(single).sendMessageForContext(eq(singleRequest), any(AiInvocationContext.class));
            verify(group).sendMessageForContext(eq(8L), eq("question"), any(AiInvocationContext.class));
            assertEquals(0, consumed.get());
            ByteArrayOutputStream singleOutput = new ByteArrayOutputStream();
            ByteArrayOutputStream groupOutput = new ByteArrayOutputStream();
            singleResponse.writeTo(singleOutput); groupResponse.writeTo(groupOutput);
            assertEquals(2, consumed.get());
            assertEquals("event:delta\ndata:{\"content\":\"forest\"}\n\n",
                    new String(singleOutput.toByteArray(), StandardCharsets.UTF_8));
            assertArrayEquals(singleOutput.toByteArray(), groupOutput.toByteArray());
        });
    }

    @Test void preparationFailureIsSynchronousAndStopRemainsSynchronous() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AiSingleChatExecutor single = context.getBean(AiSingleChatExecutor.class);
            IllegalStateException failure = new IllegalStateException("preparation rejected");
            when(single.sendMessageForContext(any(AiSingleChatRequest.class), any(AiInvocationContext.class)))
                    .thenThrow(failure);
            AiWebSingleSendRequest request = new AiWebSingleSendRequest(); request.setContent("question");
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> context.getBean(AiWebSingleController.class).send(request)));
            context.getBean(AiWebSingleController.class).stop(7L);
            verify(single).stopMessageForContext(eq(7L), any(AiInvocationContext.class));
            AiGroupChatExecutor group = context.getBean(AiGroupChatExecutor.class);
            context.getBean(AiWebGroupController.class).stop(9L);
            verify(group).stopMessageForContext(eq(9L), any(AiInvocationContext.class));
        });
    }

    @Test void permissionFailureIsReportedBeforeResponseOrCorePreparation() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AiHostPermissionPort permissions = context.getBean(AiHostPermissionPort.class);
            when(permissions.hasPermission(any(AiInvocationContext.class), anyString())).thenReturn(false);
            AiWebSingleSendRequest request = new AiWebSingleSendRequest(); request.setContent("question");
            assertThrows(io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException.class,
                    () -> context.getBean(AiWebSingleController.class).send(request));
            AiWebGroupSendRequest groupRequest = new AiWebGroupSendRequest();
            groupRequest.setConversationId(8L); groupRequest.setContent("question");
            assertThrows(io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException.class,
                    () -> context.getBean(AiWebGroupController.class).send(groupRequest));
            verifyNoInteractions(context.getBean(AiSingleChatExecutor.class), context.getBean(AiGroupChatExecutor.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AiRuntimeActivationConfiguration.class, AiStarterMarkerConfiguration.class})
    @EnableWebMvc
    static class Host {
        @Bean AiInvocationContextPort identities() {
            return new AiInvocationContextPort() {
                public AiInvocationContext capture(String actor) { return captureCurrent(); }
                public AiInvocationContext captureCurrent() { return new AiInvocationContext("test", "tenant", "actor", "invocation"); }
            };
        }
        @Bean AiHostPermissionPort permissions() {
            AiHostPermissionPort permissions = mock(AiHostPermissionPort.class);
            when(permissions.hasPermission(any(AiInvocationContext.class), anyString())).thenReturn(true);
            return permissions;
        }
        @Bean AiHostAuthenticationBridge authentication(AiInvocationContextPort identities, AiHostPermissionPort permissions) {
            AiHostSessionPort sessions = mock(AiHostSessionPort.class);
            when(sessions.currentSession(any(AiInvocationContext.class)))
                    .thenReturn(new AiHostSession("test", "tenant", "actor", "session", System.currentTimeMillis() + 60000));
            return new AiHostAuthenticationBridge("test", identities, sessions, permissions);
        }
        @Bean AiSingleChatExecutor singleCore() { return mock(AiSingleChatExecutor.class); }
        @Bean AiGroupChatExecutor groupCore() { return mock(AiGroupChatExecutor.class); }
        @Bean AiGroupChatStreamExecutor groupStreamCore() { return mock(AiGroupChatStreamExecutor.class); }
        @Bean AiHostSingleChatService single(AiHostAuthenticationBridge auth, AiSingleChatExecutor core) {
            return new AiHostSingleChatService(auth, core);
        }
        @Bean AiHostGroupChatService group(AiHostAuthenticationBridge auth, AiGroupChatExecutor core) {
            return new AiHostGroupChatService(auth, core);
        }
        @Bean AiHostConversationManagementService management(AiHostAuthenticationBridge auth) {
            return new AiHostConversationManagementService(auth, mock(AiConversationManagementService.class));
        }
        @Bean AiConversationShareService shareCore() { return mock(AiConversationShareService.class); }
        @Bean AiHostConversationShareService shares(AiHostAuthenticationBridge auth, AiConversationShareService core) {
            return new AiHostConversationShareService(auth, core);
        }
        @Bean AiPublicConversationShareService publicShares(AiConversationShareService core) {
            return new AiPublicConversationShareService(core);
        }
        @Bean AiHostGroupAgentService agents(AiHostAuthenticationBridge auth, AiInvocationContextPort identities) {
            return new AiHostGroupAgentService(auth, new AiGroupAgentCatalogService(identities, mock(AiGroupAgentCatalogPort.class)));
        }
    }
}
