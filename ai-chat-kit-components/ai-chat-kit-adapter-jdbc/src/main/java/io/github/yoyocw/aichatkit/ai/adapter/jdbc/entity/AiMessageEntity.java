package io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** ai_runtime_message 表映射；查询归属由可信作用域显式限定。 */
@Getter
@Setter
@TableName("ai_runtime_message")
public class AiMessageEntity extends AiScopedEntity {
    private Long conversationId;
    private String role;
    private Integer status;
    private String content;
    private Boolean mapEnabled;
    private Integer roundNo;
    private String speakerCode;
    private String speakerName;
    private String requestId;
    private String responseData;
    private String errorMessage;
}
