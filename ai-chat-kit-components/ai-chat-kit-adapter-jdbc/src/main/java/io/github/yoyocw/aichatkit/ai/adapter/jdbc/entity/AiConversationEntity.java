package io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.sql.Timestamp;

/** ai_runtime_conversation 表映射；查询归属由可信作用域显式限定。 */
@Getter
@Setter
@TableName("ai_runtime_conversation")
public class AiConversationEntity extends AiScopedEntity {
    private String title;
    private Boolean pinned;
    private Timestamp pinnedAt;
    private String shareCode;
    private Integer shareStatus;
    private Timestamp shareExpireAt;
    private Long shareAccessCount;
    private Timestamp shareLastAccessAt;
    private String appId;
    private String remoteSessionId;
    private Long turnId;
    private String memorySummary;
    private Long memoryCursor;
}
