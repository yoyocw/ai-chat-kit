package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * AI 对话会话重命名请求，只允许修改当前用户拥有的会话标题。
 */
@Schema(description = "管理后台 - AI 对话会话重命名请求")
@Data
public class AiChatConversationUpdateReqVO {

    /** 需要重命名的会话主键编号。 */
    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "会话编号不能为空")
    private Long id;
    /** 用户自定义会话标题，去除首尾空格后长度为 1 至 100 字符。 */
    @Schema(description = "会话标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "B-023 小班近况查询")
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 100, message = "会话标题不能超过 100 个字符")
    private String title;
}
