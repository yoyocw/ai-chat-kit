package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话响应，供左侧历史对话列表展示标题与最近时间。
 */
@Schema(description = "管理后台 - AI 对话会话响应")
@Data
public class AiChatConversationRespVO {

    /** 会话主键编号。 */
    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    /** 会话展示标题。 */
    @Schema(description = "会话标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    /** 会话最后更新时间，用于历史列表排序和时间展示。 */
    @Schema(description = "最后更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;
    /** 是否在历史会话列表中置顶。 */
    @Schema(description = "是否置顶", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean pinned;
    /** 最近置顶时间；未置顶时为空。 */
    @Schema(description = "置顶时间")
    private LocalDateTime pinnedTime;
}
