package io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;
import java.sql.Timestamp;

/** 五张 AI 表的公共字段。空字段不参与插入，保留数据库默认值和时钟。 */
@Getter
@Setter
public abstract class AiScopedEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String namespace;
    private String tenantId;
    private String actorId;
    private String mode;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Boolean deleted;
}
