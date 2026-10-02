package io.github.yoyocw.aichatkit.module.ai.contract.model;

import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable output and whitelisted metrics. */
public final class AiModelResult {
    private final String content;
    private final String requestId;
    private final String sessionId;
    private final String finishReason;
    private final String responseData;
    private final String groupResponseData;
    private final AiExecutionMetrics metrics;
    private final List<AiModelGroupReply> replies;

    public AiModelResult(String content, String requestId, String sessionId, String finishReason, String responseData, AiExecutionMetrics metrics, List<AiModelGroupReply> replies, String groupResponseData) {
        this.content = content;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.finishReason = finishReason;
        this.responseData = responseData;
        this.metrics = metrics;
        this.replies = Collections.unmodifiableList(new ArrayList<AiModelGroupReply>(replies == null ? Collections.<AiModelGroupReply>emptyList(): replies));
        this.groupResponseData = groupResponseData;
    }

    public String getContent() {
        return content;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public String getResponseData() {
        return responseData;
    }

    public String getGroupResponseData() {
        return groupResponseData;
    }

    public List<AiModelGroupReply> getReplies() {
        return replies;
    }

    public AiExecutionMetrics getMetrics() {
        return metrics;
    }

    public AiExecutionMetrics metrics(long duration) {
        return new AiExecutionMetrics(requestId, metrics == null ? null: metrics.getModelNames(), metrics == null ? null: metrics.getInputTokens(), metrics == null ? null: metrics.getOutputTokens(), metrics == null ? null: metrics.getToolCallCount(), metrics == null ? null: metrics.getFirstTokenMs(), duration);
    }
}
