package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * AI 群聊重命名请求，只允许修改当前用户拥有的群聊标题。
 */
@Schema(description = "管理后台 - AI 群聊重命名请求")
@Data
public class AiGroupChatConversationUpdateReqVO {

    /** 需要重命名的群聊会话编号。 */
    @Schema(description = "群聊会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "群聊会话编号不能为空")
    private Long id;
    /** 去除首尾空格后长度为 1 至 30 个字符的群聊标题。 */
    @Schema(description = "群聊标题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "群聊标题不能为空")
    @Size(max = 30, message = "群聊标题不能超过 30 个字符")
    private String title;
}
