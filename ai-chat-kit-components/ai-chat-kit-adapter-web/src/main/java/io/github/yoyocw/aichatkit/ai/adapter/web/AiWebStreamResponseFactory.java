package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Converts an already prepared execution to MVC SSE without deferring authentication or preparation. */
public class AiWebStreamResponseFactory {
    private final AiChatStreamEventWriter writer;

    public AiWebStreamResponseFactory(AiChatStreamEventWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** Consume only when MVC writes the response, after the preparation transaction has committed. */
    public StreamingResponseBody stream(AiPreparedExecution prepared) {
        Objects.requireNonNull(prepared, "prepared");
        return output -> prepared.consume((event, data) -> writer.write(output, event, new LinkedHashMap<>(data)));
    }
}
