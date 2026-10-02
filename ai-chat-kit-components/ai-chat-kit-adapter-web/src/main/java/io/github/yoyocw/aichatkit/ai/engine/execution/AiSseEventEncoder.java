package io.github.yoyocw.aichatkit.ai.engine.execution;

import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiExecutionEventSink;
import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Shared wire encoding for compatibility MVC adapters. */
public final class AiSseEventEncoder implements AiExecutionEventSink {
    private final OutputStream output;
    public AiSseEventEncoder(OutputStream output) { this.output = Objects.requireNonNull(output, "output"); }
    public void accept(String event, Map<String, Object> data) {
        try {
            String value = "event:" + event + "\ndata:" + AiEngineJson.toJsonString(data) + "\n\n";
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
