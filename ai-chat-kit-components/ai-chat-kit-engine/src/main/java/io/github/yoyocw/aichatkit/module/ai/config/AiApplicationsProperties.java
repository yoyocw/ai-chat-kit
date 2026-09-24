package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 部署级应用绑定，全部租户共用；修改后重启，无数据库回退或热刷新。 */
@Data
@ConfigurationProperties(prefix = "ai-chat-kit.ai.applications")
public class AiApplicationsProperties {
    /** 单聊绑定，未使用单聊时允许未配置。 */
    private AiApplicationBindingProperties single = new AiApplicationBindingProperties();
    /** 群聊绑定，未使用群聊时允许未配置。 */
    private AiApplicationBindingProperties group = new AiApplicationBindingProperties();
}
