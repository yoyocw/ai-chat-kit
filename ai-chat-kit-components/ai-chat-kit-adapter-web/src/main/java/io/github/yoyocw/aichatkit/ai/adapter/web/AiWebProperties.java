package io.github.yoyocw.aichatkit.ai.adapter.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 独立HTTP入口配置；默认不开启任何路由。 */
@ConfigurationProperties(prefix = "ai-chat-kit.ai.web")
public class AiWebProperties {
    /** 明确启用；仍要求EnableAiChatKit、引擎与真实宿主端口。 */
    private boolean enabled;
    /** 完整固定路由前缀，不再由旧宿主统一添加admin-api。 */
    private String pathPrefix = "/admin-api/ai";
    /** @return 是否明确启用 */ public boolean isEnabled() { return enabled; }
    /** @param enabled 明确开关 */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** @return 完整固定前缀 */ public String getPathPrefix() { return pathPrefix; }
    /** @param pathPrefix 固定部署路径，不能来自HTTP参数 */ public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
}

