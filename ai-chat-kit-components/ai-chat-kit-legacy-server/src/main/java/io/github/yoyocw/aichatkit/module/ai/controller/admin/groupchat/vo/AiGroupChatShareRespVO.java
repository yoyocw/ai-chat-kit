package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI 群聊公开分享响应，包含标题、固定智能体目录信息及已完成消息。
 */
@Data
@Schema(description = "AI 群聊公开分享响应")
public class AiGroupChatShareRespVO {

    /** 群聊展示标题。 */
    @Schema(description = "群聊标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    /** 群聊候选智能体，仅包含编码、名称和职责。 */
    @Schema(description = "候选智能体成员", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AiGroupChatAgentRespVO> members;
    /** 已完成的用户及智能体消息，按消息编号正序排列。 */
    @Schema(description = "已完成消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AiGroupChatShareMessageRespVO> messages;
}
