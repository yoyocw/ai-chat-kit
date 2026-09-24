package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 群聊公开分享消息响应，仅包含页面展示所需的已完成消息字段。
 */
@Data
@Schema(description = "AI 群聊公开分享消息响应")
public class AiGroupChatShareMessageRespVO {

    /** 消息角色：user 用户或 assistant 智能体。 */
    @Schema(description = "消息角色", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
    /** 实际发言智能体编码；用户消息为空。 */
    @Schema(description = "发言智能体编码")
    private String speakerCode;
    /** 实际发言智能体名称；用户消息为空。 */
    @Schema(description = "发言智能体名称")
    private String speakerName;
    /** 本轮编排中的发言顺序；用户消息为空。 */
    @Schema(description = "发言轮次")
    private Integer roundNo;
    /** 用户问题或智能体回复正文。 */
    @Schema(description = "消息正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
    /** 消息创建时间。 */
    @Schema(description = "消息创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
}
