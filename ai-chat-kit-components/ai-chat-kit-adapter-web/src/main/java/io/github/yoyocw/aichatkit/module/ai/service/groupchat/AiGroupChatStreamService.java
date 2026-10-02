package io.github.yoyocw.aichatkit.module.ai.service.groupchat;

import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebStreamResponseFactory;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMember;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.List;
import java.util.Objects;

/** Low-level MVC compatibility facade; preparation must occur inside the selected AI transaction. */
public class AiGroupChatStreamService {
    private final AiGroupChatStreamExecutor executor;
    private final AiWebStreamResponseFactory responses;
    public AiGroupChatStreamService(AiGroupChatStreamExecutor executor) {
        this(executor, new AiWebStreamResponseFactory(new AiChatStreamEventWriter()));
    }
    public AiGroupChatStreamService(AiGroupChatStreamExecutor executor, AiWebStreamResponseFactory responses) {
        this.executor = Objects.requireNonNull(executor);
        this.responses = Objects.requireNonNull(responses);
    }
    public StreamingResponseBody createResponse(AiInvocationContext context, Long conversation, String session,
            Long userId, Long message, String prompt, List<? extends AiGroupMember> members, String history,
            String trace, AiApplicationConfig config, String authorization, boolean appChanged) {
        return createResponseForContext(context, conversation, session, message, prompt, members, history,
                trace, config, authorization, appChanged);
    }
    public StreamingResponseBody createResponseForContext(AiInvocationContext context, Long conversation, String session,
            Long message, String prompt, List<? extends AiGroupMember> members, String history,
            String trace, AiApplicationConfig config, String authorization, boolean appChanged) {
        AiPreparedExecution prepared = executor.createResponseForContext(context, conversation, session, message,
                prompt, members, history, trace, config, authorization, appChanged);
        return responses.stream(prepared);
    }
}
