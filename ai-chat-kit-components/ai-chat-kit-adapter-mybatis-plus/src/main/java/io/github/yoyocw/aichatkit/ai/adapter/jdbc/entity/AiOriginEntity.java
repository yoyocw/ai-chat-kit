package io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** ai_runtime_origin 表映射；查询归属由可信作用域显式限定。 */
@Getter
@Setter
@TableName("ai_runtime_origin")
public class AiOriginEntity extends AiScopedEntity {
    private Long messageId;
    private String clientRecordId;
    private String clientId;
    private String businessSystem;
    private String environment;
    private String appId;
}
