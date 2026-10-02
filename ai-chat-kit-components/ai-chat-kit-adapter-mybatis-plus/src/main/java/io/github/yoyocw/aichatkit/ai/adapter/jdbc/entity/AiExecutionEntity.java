package io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** ai_runtime_execution 表映射；查询归属由可信作用域显式限定。 */
@Getter
@Setter
@TableName("ai_runtime_execution")
public class AiExecutionEntity extends AiScopedEntity {
    private Long conversationId;
    private Long messageId;
    private String appId;
    private String traceCode;
    private Integer status;
    private Integer retryCount;
    private String requestId;
    private String modelNames;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer toolCallCount;
    private Long firstTokenMs;
    private Long totalDurationMs;
    private String errorCode;
}
