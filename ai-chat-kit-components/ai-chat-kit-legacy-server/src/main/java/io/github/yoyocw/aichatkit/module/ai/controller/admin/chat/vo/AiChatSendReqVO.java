package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * AI 消息发送请求，支持在首次发送时自动创建会话并调用服务端固定百炼应用。
 */
@Schema(description = "管理后台 - AI 消息发送请求")
@Data
public class AiChatSendReqVO implements io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest {

    /** 已有会话编号；新对话首次发送时可为空，由服务自动创建。 */
    @Schema(description = "会话编号，新对话首次发送可为空", example = "1024")
    private Long conversationId;
    /** 用户本轮问题正文，最大 10000 字符，避免超大输入占用服务资源。 */
    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "查询 B-023 小班近况")
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 10000, message = "消息内容不能超过 10000 个字符")
    private String content;
    /** 是否要求百炼工作流执行地图查询或返回地图卡片数据。 */
    @Schema(description = "是否开启地图业务结果", defaultValue = "true")
    private Boolean mapEnabled = true;
}
