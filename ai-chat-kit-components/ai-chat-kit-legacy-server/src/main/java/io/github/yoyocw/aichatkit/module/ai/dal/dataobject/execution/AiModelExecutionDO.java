package io.github.yoyocw.aichatkit.module.ai.dal.dataobject.execution;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AI 模型请求级执行审计对象，记录一次单聊或群聊工作流的可观测指标与终态。
 */
@TableName("ai_model_execution")
@KeySequence("ai_model_execution_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiModelExecutionDO extends TenantBaseDO {

    /** 执行审计主键编号，由 PostgreSQL 序列生成。 */
    @TableId
    private Long id;
    /** 业务会话编号；需结合 mode 判断单聊或群聊会话表。 */
    private Long conversationId;
    /** 本轮助手占位消息编号；需结合 mode 判断消息表。 */
    private Long messageId;
    /** 发起本轮执行的后台用户编号。 */
    private Long userId;
    /** 执行模式：single 普通对话、group 多智能体群聊。 */
    private String mode;
    /** 服务端生成的请求链路追踪码，最长 64 字符。 */
    private String traceCode;
    /** 本轮实际调用的已发布百炼应用 ID。 */
    private String appId;
    /** 百炼应用版本；上游未返回可靠版本时为空。 */
    private String appVersion;
    /** 百炼 usage 返回的模型 ID，多个模型按英文逗号连接。 */
    private String modelNames;
    /** 百炼平台请求编号，用于关联平台侧调用日志。 */
    private String requestId;
    /** 执行状态：0 执行中、1 完成、2 停止、3 失败。 */
    private Integer status;
    /** 百炼短期会话失效后的重试次数，当前最多 1 次。 */
    private Integer retryCount;
    /** 本轮所有模型合计输入 Token 数；上游未返回时为空。 */
    private Integer inputTokens;
    /** 本轮所有模型合计输出 Token 数；上游未返回时为空。 */
    private Integer outputTokens;
    /** 百炼 thoughts 中可识别的工具调用步骤数量。 */
    private Integer toolCallCount;
    /** 从发起百炼 HTTP 调用到收到首段正文的耗时，单位毫秒。 */
    private Long firstTokenMs;
    /** 从开始流式执行到消息进入终态的总耗时，单位毫秒。 */
    private Long totalDurationMs;
    /** 预估调用费用；未配置可信计价规则时为空。 */
    private BigDecimal estimatedCost;
    /** 失败、停止或超时的稳定错误编码，成功时为空。 */
    private String errorCode;
    /** 实际执行实例，格式为 Spring 应用名@主机名。 */
    private String instanceId;
}
