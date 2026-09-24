package io.github.yoyocw.aichatkit.module.ai.contract.audit;

/** 平台无关的执行指标快照，不包含正文、会话信息或凭据。 */
public final class AiExecutionMetrics {
    /** 上游请求编号，仅用于审计追踪。 */
    private final String requestId;
    /** 模型名称，多个模型使用英文逗号连接。 */
    private final String modelNames;
    /** 输入 Token 总数；上游未提供时为空。 */
    private final Integer inputTokens;
    /** 输出 Token 总数；上游未提供时为空。 */
    private final Integer outputTokens;
    /** 可识别的工具调用次数；未提供时为空。 */
    private final Integer toolCallCount;
    /** 首段正文延迟，单位毫秒；未提供时为空。 */
    private final Long firstTokenMs;

    /** 业务执行总耗时，单位毫秒。 */
    private final long totalDurationMs;

    /** 保存上游原始指标；缺失值保持为空，不转换为零。
     * @param requestId 上游请求编号，仅用于审计追踪。
     * @param modelNames 模型名称，多个模型使用英文逗号连接。
     * @param inputTokens 输入 Token 总数；上游未提供时为空。
     * @param outputTokens 输出 Token 总数；上游未提供时为空。
     * @param toolCallCount 可识别的工具调用次数；未提供时为空。
     * @param firstTokenMs 首段正文延迟，单位毫秒；未提供时为空。
     * @param totalDurationMs 业务总耗时，单位毫秒
     */
    public AiExecutionMetrics(String requestId, String modelNames, Integer inputTokens, Integer outputTokens,
                              Integer toolCallCount, Long firstTokenMs, long totalDurationMs) {
        this.requestId = requestId;
        this.modelNames = modelNames;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.toolCallCount = toolCallCount;
        this.firstTokenMs = firstTokenMs;
        this.totalDurationMs = totalDurationMs;
    }

    /** @return 上游请求编号，仅用于审计追踪。 */
    public String getRequestId() {
        return requestId;
    }

    /** @return 模型名称，多个模型使用英文逗号连接。 */
    public String getModelNames() {
        return modelNames;
    }

    /** @return 输入 Token 总数；上游未提供时为空。 */
    public Integer getInputTokens() {
        return inputTokens;
    }

    /** @return 输出 Token 总数；上游未提供时为空。 */
    public Integer getOutputTokens() {
        return outputTokens;
    }

    /** @return 可识别的工具调用次数；未提供时为空。 */
    public Integer getToolCallCount() {
        return toolCallCount;
    }

    /** @return 首段正文延迟，单位毫秒；未提供时为空。 */
    public Long getFirstTokenMs() {
        return firstTokenMs;
    }

    /** @return 业务总耗时，单位毫秒。 */
    public long getTotalDurationMs() {
        return totalDurationMs;
    }
}
