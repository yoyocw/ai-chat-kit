package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 群聊会话响应，包含标题、候选成员和最近更新时间。
 */
@Schema(description = "管理后台 - AI 群聊会话响应")
@Data
public class AiGroupChatConversationRespVO {

    /** 群聊会话主键编号。 */
    @Schema(description = "群聊会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    /** 群聊展示标题。 */
    @Schema(description = "群聊标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    /** 群聊候选智能体成员，顺序与创建或调整时一致。 */
    @Schema(description = "候选智能体成员", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AiGroupChatAgentRespVO> members;
    /** 是否置顶显示。 */
    @Schema(description = "是否置顶", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean pinned;
    /** 最近一次置顶时间，未置顶时为空。 */
    @Schema(description = "置顶时间")
    private LocalDateTime pinnedTime;
    /** 群聊创建时间。 */
    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
    /** 群聊最后更新时间，用于历史列表排序。 */
    @Schema(description = "最后更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;
}
