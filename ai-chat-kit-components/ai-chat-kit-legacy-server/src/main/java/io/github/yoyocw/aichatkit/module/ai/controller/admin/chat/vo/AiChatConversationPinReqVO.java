package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * AI 会话置顶状态更新请求，供单聊与群聊复用且仅允许操作当前用户拥有的会话。
 */
@Data
@Schema(description = "管理后台 - AI 会话置顶状态更新请求")
public class AiChatConversationPinReqVO {

    /** 需要置顶或取消置顶的会话编号。 */
    @NotNull(message = "会话编号不能为空")
    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    /** 目标置顶状态：true 置顶，false 取消置顶。 */
    @NotNull(message = "置顶状态不能为空")
    @Schema(description = "是否置顶", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean pinned;
}
