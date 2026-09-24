package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 会话公开分享响应，供单聊与群聊复用并明确返回本次分享的过期时间。
 */
@Data
@Schema(description = "管理后台 - AI 会话公开分享响应")
public class AiChatConversationShareRespVO {

    /** 不可枚举的 32 位十六进制分享码。 */
    @Schema(description = "分享码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String shareCode;
    /** 服务端生成的完整公开访问地址。 */
    @Schema(description = "分享地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String shareUrl;
    /** 分享过期时间戳，单位毫秒，由服务端根据有效天数计算。 */
    @Schema(description = "分享过期时间戳，单位毫秒", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expireTime;
}
