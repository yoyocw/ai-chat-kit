package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 宿主的部署级密钥管理开关；仅控制候选管理能力，不自动迁移或生成密钥。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-chat-kit.ai.key-management")
public class AiKeyManagementProperties {
    /** 默认关闭；AI 数据源归属及平台管理员认证能力验证完成后才可启用。 */
    private boolean enabled;
}
