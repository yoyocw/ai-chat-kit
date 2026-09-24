package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * AI 会话公开分享创建请求，供单聊与群聊复用且仅接受当前用户拥有的会话编号。
 */
@Data
@Schema(description = "管理后台 - AI 会话公开分享创建请求")
public class AiChatConversationShareReqVO {

    /** 需要创建或复用公开分享的会话编号。 */
    @NotNull(message = "会话编号不能为空")
    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /** 分享有效天数；未传时由服务端按 7 天处理，允许范围为 1 至 30 天。 */
    @Min(value = 1, message = "分享有效天数不能少于 1 天")
    @Max(value = 30, message = "分享有效天数不能超过 30 天")
    @Schema(description = "分享有效天数，默认 7 天", example = "7")
    private Integer validDays;
}
