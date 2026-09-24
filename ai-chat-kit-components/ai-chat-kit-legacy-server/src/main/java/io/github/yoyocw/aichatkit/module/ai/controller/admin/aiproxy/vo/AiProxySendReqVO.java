package io.github.yoyocw.aichatkit.module.ai.controller.admin.aiproxy.vo;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/** 业务后端对话发送请求，不允许用户指定 AI 地址或凭据。 */
@Getter
@Setter
public class AiProxySendReqVO {
    /** 现有会话编号，单聊可为空，群聊必须指定。 */
    @Positive(message = "会话编号必须为正数")
    private Long conversationId;
    /** 用户本轮问题，最多 10000 字符。 */
    @NotBlank
    @Size(max = 10000)
    private String content;
    /** 单聊地图开关，群聊忽略。 */
    private Boolean mapEnabled = true;
}
