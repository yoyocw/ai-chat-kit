package io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** ai_runtime_member 表映射；查询归属由可信作用域显式限定。 */
@Getter
@Setter
@TableName("ai_runtime_member")
public class AiMemberEntity extends AiScopedEntity {
    private Long conversationId;
    private String agentCode;
    private String agentName;
    private String agentRole;
    private Integer sortOrder;
}
