package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 公开分享消息响应，仅包含完成消息的展示安全字段。
 */
@Data
@Schema(description = "AI 对话公开分享消息响应")
public class AiChatShareMessageRespVO {

    /** 消息角色，仅为 user 或 assistant。 */
    @Schema(description = "消息角色", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
    /** 用户问题或已完成模型回复正文。 */
    @Schema(description = "消息正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
    /** 消息创建时间。 */
    @Schema(description = "消息创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
}
