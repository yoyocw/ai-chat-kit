package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 对话公开查看响应，不包含所有者、平台追踪及扩展业务敏感字段。
 */
@Data
@Schema(description = "AI 对话公开查看响应")
public class AiChatShareRespVO {

    /** 被分享会话的展示标题。 */
    @Schema(description = "会话标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    /** 会话内按时间顺序排列的已完成消息。 */
    @Schema(description = "已完成消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AiChatShareMessageRespVO> messages;
}
