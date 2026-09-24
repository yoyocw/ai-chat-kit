package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import lombok.Data;

/**
 * 百炼流式调用完成结果，汇总最终文本、平台请求编号与调用期内的上游扩展节点。
 */
@Data
public class BailianStreamResult {

    /** 本次流式响应拼接后的完整回复正文。 */
    private String content;
    /** 百炼平台请求编号，用于平台日志追踪。 */
    private String requestId;
    /** 百炼 Agent 2.0 会话标识，用于后续问题延续短期上下文。 */
    private String sessionId;
    /** 百炼生成结束原因，正常完成固定为 stop。 */
    private String finishReason;
    /** 百炼最后一个 output 节点的临时 JSON；业务层必须白名单转换后才能持久化或输出。 */
    private String responseData;
    /** 百炼 usage 返回的模型 ID，多个模型按英文逗号连接。 */
    private String modelNames;
    /** 本轮所有模型合计输入 Token 数；上游未返回 usage 时为空。 */
    private Integer inputTokens;
    /** 本轮所有模型合计输出 Token 数；上游未返回 usage 时为空。 */
    private Integer outputTokens;
    /** 百炼 thoughts 中可识别的工具调用步骤数量。 */
    private Integer toolCallCount;
    /** 从发起百炼 HTTP 调用到收到首段正文的耗时，单位毫秒。 */
    private Long firstTokenMs;
}
