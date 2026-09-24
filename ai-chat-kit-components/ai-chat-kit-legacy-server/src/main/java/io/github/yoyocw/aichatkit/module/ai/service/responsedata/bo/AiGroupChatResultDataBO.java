package io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo;

import lombok.Data;

/**
 * 多智能体群聊安全汇总结果，只保留页面消费所需的工作流白名单字段。
 */
@Data
public class AiGroupChatResultDataBO {

    /** 工作流业务状态稳定编码；工作流未提供时为空。 */
    private String status;
    /** 业务链路安全追踪码，不得包含租户、用户、令牌或内部路径。 */
    private String traceCode;
    /** 多智能体编排后的最终汇总文本；工作流未提供时为空。 */
    private String finalAnswer;
    /** 工作流是否完成全部编排步骤；工作流未提供时为空。 */
    private Boolean completed;
    /** 工作流停止原因稳定编码或安全说明；正常完成或未提供时为空。 */
    private String stopReason;
}
