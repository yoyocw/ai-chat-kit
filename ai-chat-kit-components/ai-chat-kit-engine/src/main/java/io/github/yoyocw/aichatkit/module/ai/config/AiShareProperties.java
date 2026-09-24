package io.github.yoyocw.aichatkit.module.ai.config;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

/** 可移植分享地址配置，沿用既有YAML键和默认路径，不从访客Host头拼接地址。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-chat-kit.ai.chat")
public class AiShareProperties {
    /** 单聊分享可信HTTP(S)基础地址，部署者可指向自己的分享页面。 */
    private String shareBaseUrl = "http://localhost:48080/admin-api/ai/chat/share";
    /** 群聊分享可信HTTP(S)基础地址，保持原默认路径。 */
    private String groupShareBaseUrl = "http://localhost:48080/admin-api/ai/group-chat/share";

    /** @param mode 固定聊天模式 @return 校验后的可信基础地址，不带末尾斜线 */
    public String baseUrl(AiChatMode mode) {
        if (mode == null) { throw new IllegalArgumentException("AI 分享模式缺失"); }
        String value = mode == AiChatMode.SINGLE ? shareBaseUrl : groupShareBaseUrl;
        try {
            URI uri = new URI(value);
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            while (value.endsWith("/")) { value = value.substring(0, value.length() - 1); }
            return value;
        } catch (Exception ex) {
            throw new IllegalStateException("AI 分享基础地址须为不含用户凭据、查询参数和片段的绝对HTTP(S)地址");
        }
    }
}
