package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * AI 群聊消息发送请求，消息必须发送到已经配置成员的现有群聊。
 */
@Schema(description = "管理后台 - AI 群聊消息发送请求")
@Data
public class AiGroupChatSendReqVO {

    /** 目标群聊会话编号。 */
    @Schema(description = "群聊会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "群聊会话编号不能为空")
    private Long conversationId;
    /** 用户本轮问题正文，最多 10000 个字符。 */
    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 10000, message = "消息内容不能超过 10000 个字符")
    private String content;
}
