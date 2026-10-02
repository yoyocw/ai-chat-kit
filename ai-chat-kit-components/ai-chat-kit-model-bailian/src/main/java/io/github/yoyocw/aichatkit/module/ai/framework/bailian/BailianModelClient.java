package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelEvent;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelException;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelGroupReply;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelResult;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelSessionExpiredException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_CONFIG_INVALID;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_RESPONSE_INCOMPLETE;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.GROUP_WORKFLOW_OUTPUT_INVALID;

/** Default provider adapter; owns protocol mapping, safe error translation and group validation. */
public final class BailianModelClient implements AiModelClient {
    private final BailianClient transport;
    private final BailianGroupOutputParser parser;

    public BailianModelClient(BailianClient transport, BailianGroupOutputParser parser) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public boolean isConfigured(AiChatMode mode, String appId) {
        return mode == AiChatMode.GROUP
                ? transport.isGroupWorkflowConfigured(appId) : transport.isConfigured(appId);
    }

    @Override
    public void cancel(AiChatMode mode, Long id) {
        if (mode == AiChatMode.GROUP) {
            transport.cancelGroupWorkflow(id);
        } else {
            transport.cancel(id);
        }
    }

    @Override
    public AiModelResult stream(AiModelRequest request, Consumer<AiModelEvent> events,
                                BooleanSupplier stillGenerating) throws IOException {
        try {
            AiApplicationConfig config = request.getApplication();
            List<String> tools = config == null ? Collections.emptyList() : config.getUserAuthToolIds();
            String page = BailianWorkflowBizParams.buildPageContextJson(
                    request.getMode() == AiChatMode.GROUP ? "group" : "single", request.getMapEnabled());
            BailianStreamResult result;
            if (request.getMode() == AiChatMode.GROUP) {
                Map<String, Object> params = BailianWorkflowBizParams.build("", page,
                        request.getBusinessArtifactJson(), request.getHistorySummary(),
                        request.getTraceCode(), request.getMembers());
                BailianWorkflowBizParams.appendUserTokens(params, tools, request.toolAuthorization());
                result = transport.streamGroupWorkflow(request.getMessageId(), request.getAppId(),
                        request.getPrompt(), request.getSessionId(), params, stillGenerating);
            } else {
                Map<String, Object> params = BailianWorkflowBizParams.buildAgentPromptParams(page,
                        request.getBusinessArtifactJson(), request.getHistorySummary(),
                        config == null ? null : config.getMcpId(), request.toolAuthorization(),
                        tools, request.getTraceCode());
                result = transport.stream(request.getMessageId(), request.getAppId(), request.getPrompt(),
                        request.getSessionId(), params, event -> {
                            if (events != null) {
                                events.accept(event.getType() == BailianStreamEventType.DELTA
                                        ? AiModelEvent.delta(event.getContent())
                                        : AiModelEvent.progress(event.getStage(), event.getMessage(), event.getActionName()));
                            }
                        }, stillGenerating);
            }
            if (result == null) {
                throw new AiModelException(BAILIAN_RESPONSE_INCOMPLETE, true);
            }
            List<AiModelGroupReply> replies = new ArrayList<>();
            String groupData = null;
            if (request.getMode() == AiChatMode.GROUP) {
                List<String> codes = new ArrayList<>();
                request.getMembers().forEach(member -> codes.add(member.getCode()));
                BailianGroupOutput output = parser.parse(result.getContent(), codes);
                for (BailianGroupReply reply : output.getReplies()) {
                    replies.add(new AiModelGroupReply(reply.getSpeakerCode(), reply.getContent()));
                }
                groupData = output.getResponseData() == null ? null : output.getResponseData().toString();
            }
            return new AiModelResult(result.getContent(), result.getRequestId(), result.getSessionId(),
                    result.getFinishReason(), result.getResponseData(),
                    BailianExecutionMetricsMapper.map(result, 0), replies, groupData);
        } catch (BailianSessionExpiredException ex) {
            throw new AiModelSessionExpiredException();
        } catch (BailianGroupOutputException ex) {
            throw new AiModelException(GROUP_WORKFLOW_OUTPUT_INVALID, false);
        } catch (BailianCallException ex) {
            throw new AiModelException(ex.getErrorCode(), ex.isRetryable());
        } catch (IllegalArgumentException ex) {
            throw new AiModelException(BAILIAN_CONFIG_INVALID, false);
        } catch (IOException ex) {
            throw AiModelException.from(ex);
        }
    }
}
