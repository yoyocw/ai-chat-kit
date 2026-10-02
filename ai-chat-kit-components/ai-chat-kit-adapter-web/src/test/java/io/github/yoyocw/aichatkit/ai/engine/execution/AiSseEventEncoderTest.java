package io.github.yoyocw.aichatkit.ai.engine.execution;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
class AiSseEventEncoderTest {
    @Test void preservesWireProtocolAndFlushesEachEvent() {
        class Output extends ByteArrayOutputStream { int flushes; public void flush() { flushes++; } }
        Output output = new Output();
        new AiSseEventEncoder(output).accept("delta", Collections.singletonMap("content", "森林\n"));
        assertEquals("event:delta\ndata:{\"content\":\"森林\\n\"}\n\n", new String(output.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(1, output.flushes);
    }
    @Test void propagatesDisconnectAsUncheckedIo() {
        IOException failure = new IOException("disconnected");
        OutputStream output = new OutputStream() { public void write(int value) throws IOException { throw failure; } };
        UncheckedIOException actual = assertThrows(UncheckedIOException.class, () -> new AiSseEventEncoder(output).accept("done", Collections.emptyMap()));
        assertSame(failure, actual.getCause());
    }
}
