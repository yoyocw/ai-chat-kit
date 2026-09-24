package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionMetrics;

/** 百炼响应到中立审计指标的白名单映射，不传递正文、会话或扩展数据。 */
public final class BailianExecutionMetricsMapper {
    private BailianExecutionMetricsMapper() { }

    /**
     * 创建不可变快照，保留上游缺失指标的 null 语义。
     * @param result 已完成的上游响应，不得为空
     * @param totalDurationMs 业务总耗时，单位毫秒
     * @return 仅包含审计指标的快照
     */
    public static AiExecutionMetrics map(BailianStreamResult result, long totalDurationMs) {
        return new AiExecutionMetrics(result.getRequestId(), result.getModelNames(),
                result.getInputTokens(), result.getOutputTokens(), result.getToolCallCount(),
                result.getFirstTokenMs(), totalDurationMs);
    }
}
