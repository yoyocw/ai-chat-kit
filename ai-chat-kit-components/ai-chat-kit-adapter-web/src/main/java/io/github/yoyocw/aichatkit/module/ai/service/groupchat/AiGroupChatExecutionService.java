package io.github.yoyocw.aichatkit.module.ai.service.groupchat;

import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebStreamResponseFactory;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.List;
import java.util.Objects;

/** Existing group API delegates to one neutral core and the shared SSE encoder. */
public class AiGroupChatExecutionService {
    private final AiGroupChatExecutor executor;
    private final AiWebStreamResponseFactory responses;
    public AiGroupChatExecutionService(AiGroupChatExecutor executor) {
        this(executor, new AiWebStreamResponseFactory(new AiChatStreamEventWriter()));
    }
    public AiGroupChatExecutionService(AiGroupChatExecutor executor, AiWebStreamResponseFactory responses) {
        this.executor = Objects.requireNonNull(executor);
        this.responses = Objects.requireNonNull(responses);
    }
    public Long createConversationForContext(String title, List<String> members, AiInvocationContext context) {
        return executor.createConversationForContext(title, members, context);
    }
    public StreamingResponseBody sendMessageForContext(Long conversation, String content, AiInvocationContext context) {
        AiPreparedExecution prepared = prepareMessageForContext(conversation, content, context);
        return responses.stream(prepared);
    }
    public AiPreparedExecution prepareMessageForContext(Long conversation, String content, AiInvocationContext context) {
        return executor.sendMessageForContext(conversation, content, context);
    }
    public void stopMessageForContext(Long messageId, AiInvocationContext context) {
        executor.stopMessageForContext(messageId, context);
    }
}
