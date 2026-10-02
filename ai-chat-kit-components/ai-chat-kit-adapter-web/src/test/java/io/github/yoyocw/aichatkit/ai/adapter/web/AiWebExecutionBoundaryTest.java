package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivationConfiguration;
import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterMarker;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiSingleChatExecutor;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutor;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamExecutor;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Host-owned controllers can use legacy MVC facades without selecting Starter or built-in routes. */
class AiWebExecutionBoundaryTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(HostMvcAndCores.class)
            .withConfiguration(AutoConfigurations.of(AiWebExecutionAutoConfiguration.class, AiWebAutoConfiguration.class));

    @Test void activatedEngineWithOwnControllerAndWebDisabledKeepsLegacyWrappers() {
        runner.withUserConfiguration(RuntimeEnabledHost.class, OwnControllerConfiguration.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(AiStarterMarker.class)
                            .hasSingleBean(AiChatStreamEventWriter.class).hasSingleBean(AiWebStreamResponseFactory.class)
                            .hasSingleBean(AiChatExecutionService.class).hasSingleBean(AiGroupChatExecutionService.class)
                            .hasSingleBean(AiGroupChatStreamService.class).hasSingleBean(OwnController.class);
                    assertBuiltInWebAbsent(context);
                    assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet())
                            .anyMatch(mapping -> mapping.getPatternValues().contains("/host/stream"));
                    AtomicInteger consumed = new AtomicInteger();
                    AiPreparedExecution prepared = sink -> {
                        consumed.incrementAndGet();
                        sink.accept("delta", Collections.singletonMap("content", "forest"));
                    };
                    AiSingleChatExecutor core = context.getBean(AiSingleChatExecutor.class);
                    when(core.sendMessageForActor(any(AiSingleChatRequest.class), eq("actor"))).thenReturn(prepared);
                    StreamingResponseBody response = context.getBean(OwnController.class).stream();
                    verify(core).sendMessageForActor(any(AiSingleChatRequest.class), eq("actor"));
                    assertEquals(0, consumed.get());
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    response.writeTo(output);
                    assertEquals(1, consumed.get());
                    assertEquals("event:delta\ndata:{\"content\":\"forest\"}\n\n",
                            new String(output.toByteArray(), StandardCharsets.UTF_8));
                });
    }

    @Test void engineDisabledCreatesNoWrappersEvenWithRuntimeMarker() {
        runner.withUserConfiguration(RuntimeEnabledHost.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false", "ai-chat-kit.ai.web.enabled=true")
                .run(this::assertAllWebAbsent);
    }

    @Test void enabledPropertiesWithoutRuntimeMarkerCreateNoWrappers() {
        runner.withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=true")
                .run(this::assertAllWebAbsent);
    }

    @Test void lowLevelAutoConfigurationHonorsExistingWriterOverrides() {
        AtomicInteger writes = new AtomicInteger();
        AiChatStreamEventWriter writer = new AiChatStreamEventWriter() {
            @Override public void write(OutputStream output, String event, Map<String, Object> data) {
                data.put("host", "custom");
                writes.incrementAndGet();
                super.write(output, event, data);
            }
        };
        runner.withUserConfiguration(RuntimeEnabledHost.class)
                .withBean(AiChatStreamEventWriter.class, () -> writer)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.web.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AiChatStreamEventWriter.class);
                    assertSame(writer, context.getBean(AiChatStreamEventWriter.class));
                    Map<String, Object> original = Collections.singletonMap("content", "forest");
                    AiPreparedExecution prepared = sink -> sink.accept("delta", original);
                    AiSingleChatExecutor core = context.getBean(AiSingleChatExecutor.class);
                    when(core.sendMessageForActor(any(AiSingleChatRequest.class), eq("actor"))).thenReturn(prepared);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    context.getBean(AiChatExecutionService.class)
                            .sendMessageForActor(mock(AiSingleChatRequest.class), "actor").writeTo(output);
                    assertEquals(1, writes.get());
                    assertFalse(original.containsKey("host"));
                    assertTrue(new String(output.toByteArray(), StandardCharsets.UTF_8).contains("\"host\":\"custom\""));
                });
    }

    private void assertAllWebAbsent(AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed().doesNotHaveBean(AiChatStreamEventWriter.class)
                .doesNotHaveBean(AiWebStreamResponseFactory.class).doesNotHaveBean(AiChatExecutionService.class)
                .doesNotHaveBean(AiGroupChatExecutionService.class).doesNotHaveBean(AiGroupChatStreamService.class);
        assertBuiltInWebAbsent(context);
    }

    private void assertBuiltInWebAbsent(AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed().doesNotHaveBean(AiWebActivation.class).doesNotHaveBean(AiWebIdentity.class)
                .doesNotHaveBean(AiWebSingleController.class).doesNotHaveBean(AiWebGroupController.class)
                .doesNotHaveBean(AiWebErrorAdvice.class);
        assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().values())
                .noneMatch(handler -> AiWebSingleController.class.isAssignableFrom(handler.getBeanType())
                        || AiWebGroupController.class.isAssignableFrom(handler.getBeanType()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class HostMvcAndCores {
        @Bean AiSingleChatExecutor singleCore() { return mock(AiSingleChatExecutor.class); }
        @Bean AiGroupChatExecutor groupCore() { return mock(AiGroupChatExecutor.class); }
        @Bean AiGroupChatStreamExecutor groupStreamCore() { return mock(AiGroupChatStreamExecutor.class); }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AiRuntimeActivationConfiguration.class)
    static class RuntimeEnabledHost { }

    @Configuration(proxyBeanMethods = false)
    static class OwnControllerConfiguration {
        @Bean OwnController ownController(AiChatExecutionService execution) { return new OwnController(execution); }
    }

    @RestController
    static class OwnController {
        private final AiChatExecutionService execution;
        OwnController(AiChatExecutionService execution) { this.execution = execution; }
        @GetMapping(value = "/host/stream", produces = "text/event-stream")
        public StreamingResponseBody stream() {
            return execution.sendMessageForActor(mock(AiSingleChatRequest.class), "actor");
        }
    }
}
