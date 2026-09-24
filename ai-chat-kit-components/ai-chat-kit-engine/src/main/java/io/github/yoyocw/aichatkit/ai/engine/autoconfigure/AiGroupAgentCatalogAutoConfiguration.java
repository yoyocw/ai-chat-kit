package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.adapter.config.YamlGroupAgentCatalogAdapter;
import io.github.yoyocw.aichatkit.module.ai.config.AiGroupAgentsProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅部署者实际配置成员后装配 YAML 目录，宿主也可提供真实数据库目录替代。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiGroupAgentsProperties.class)
@AutoConfigureBefore(name = {"io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure.AiJdbcStorageAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration"})
public class AiGroupAgentCatalogAutoConfiguration {
    /** 缺配置保留端口缺口，不能把空目录标记为可执行群聊。 */
    @Bean
    @ConditionalOnMissingBean(AiGroupAgentCatalogPort.class)
    @ConditionalOnProperty(prefix = "ai-chat-kit.ai.group", name = "agents[0].code")
    public AiGroupAgentCatalogPort aiGroupAgentCatalogPort(AiGroupAgentsProperties properties) {
        return new YamlGroupAgentCatalogAdapter(properties);
    }
}
