package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Data;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * AI 对话业务配置，定义服务端生成单聊和群聊公开分享地址所需的可信基础地址。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "ai-chat-kit.ai.chat")
public class AiChatProperties {

    /** 公开分享接口基础地址，必须为绝对 HTTP(S) URL 且末尾不要求携带斜杠。 */
    @NotBlank
    @URL
    private String shareBaseUrl = "http://localhost:48080/admin-api/ai/chat/share";
    /** 群聊公开分享接口基础地址，必须为绝对 HTTP(S) URL 且末尾不要求携带斜杠。 */
    @NotBlank
    @URL
    private String groupShareBaseUrl = "http://localhost:48080/admin-api/ai/group-chat/share";
}
