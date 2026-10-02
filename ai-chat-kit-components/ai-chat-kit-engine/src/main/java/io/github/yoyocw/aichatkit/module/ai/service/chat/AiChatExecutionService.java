package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.Objects;

/** Existing MVC API delegates to the same transport-independent execution core. */
public class AiChatExecutionService {
    private final AiSingleChatExecutor executor;
    private final AiChatStreamEventWriter writer;
    public AiChatExecutionService(AiSingleChatExecutor executor, AiChatStreamEventWriter writer) {
        this.executor = Objects.requireNonNull(executor);
        this.writer = Objects.requireNonNull(writer);
    }
    public StreamingResponseBody sendMessage(AiSingleChatRequest request, Long actor) {
        return response(executor.sendMessage(request, actor));
    }
    public StreamingResponseBody sendMessageForActor(AiSingleChatRequest request, String actor) {
        return response(executor.sendMessageForActor(request, actor));
    }
    public StreamingResponseBody sendMessageForContext(AiSingleChatRequest request, AiInvocationContext context) {
        return response(prepareMessageForContext(request, context));
    }
    public AiPreparedExecution prepareMessageForContext(AiSingleChatRequest request, AiInvocationContext context) {
        return executor.sendMessageForContext(request, context);
    }
    public void stopMessage(Long messageId, Long actor) { executor.stopMessage(messageId, actor); }
    public void stopMessageForActor(Long messageId, String actor) { executor.stopMessageForActor(messageId, actor); }
    public void stopMessageForContext(Long messageId, AiInvocationContext context) {
        executor.stopMessageForContext(messageId, context);
    }
    private StreamingResponseBody response(AiPreparedExecution prepared) {
        return output -> prepared.consume((event, data) -> writer.write(output, event, new java.util.LinkedHashMap<>(data)));
    }
}
