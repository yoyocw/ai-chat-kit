package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/** 群聊真实成员目录配置；空目录不表示已具备群聊业务能力。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-chat-kit.ai.group")
public class AiGroupAgentsProperties {
    /** 部署者定义的成员列表，各编码必须唯一。 */
    private List<AiGroupAgentProperties> agents = new ArrayList<AiGroupAgentProperties>();
}
