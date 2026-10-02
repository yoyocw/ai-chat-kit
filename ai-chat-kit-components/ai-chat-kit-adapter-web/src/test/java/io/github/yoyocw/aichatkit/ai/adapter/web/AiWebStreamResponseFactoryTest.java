package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelEvent;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiWebStreamResponseFactoryTest {
    @Test void defersConsumptionAndCopiesDataForExistingWriterOverrides() throws Exception {
        Map<String, Object> original = Collections.singletonMap("content", "forest");
        AtomicInteger calls = new AtomicInteger();
        AiChatStreamEventWriter writer = new AiChatStreamEventWriter() {
            @Override public void write(OutputStream output, String event, Map<String, Object> data) {
                assertNotSame(original, data);
                data.put("host", "custom");
                calls.incrementAndGet();
                super.write(output, event, data);
            }
        };
        AiPreparedExecution prepared = sink -> sink.accept("delta", original);
        StreamingResponseBody response = new AiWebStreamResponseFactory(writer).stream(prepared);
        assertEquals(0, calls.get());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.writeTo(output);
        assertEquals(1, calls.get());
        assertFalse(original.containsKey("host"));
        assertEquals("event:delta\ndata:{\"content\":\"forest\",\"host\":\"custom\"}\n\n",
                new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test void propagatesDisconnectToPreparedExecution() {
        IOException failure = new IOException("disconnected");
        OutputStream output = new OutputStream() {
            @Override public void write(int value) throws IOException { throw failure; }
        };
        StreamingResponseBody response = new AiWebStreamResponseFactory(new AiChatStreamEventWriter())
                .stream(sink -> sink.accept("done", Collections.emptyMap()));
        UncheckedIOException actual = assertThrows(UncheckedIOException.class, () -> response.writeTo(output));
        assertSame(failure, actual.getCause());
    }

    @Test void neutralModelHelperPreservesDeltaAndProgressProtocol() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AiChatStreamEventWriter writer = new AiChatStreamEventWriter();
        writer.writeModelEvent(output, 7L, AiModelEvent.delta("forest"));
        writer.writeModelEvent(output, 7L, AiModelEvent.progress("tool", "reading", "search"));
        assertEquals("event:delta\ndata:{\"messageId\":7,\"content\":\"forest\"}\n\n"
                + "event:progress\ndata:{\"messageId\":7,\"stage\":\"tool\",\"message\":\"reading\",\"actionName\":\"search\"}\n\n",
                new String(output.toByteArray(), StandardCharsets.UTF_8));
    }
}
