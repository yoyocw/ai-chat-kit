package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.adapter.config.YamlApplicationConfigAdapter;
import io.github.yoyocw.aichatkit.module.ai.config.AiApplicationsProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 启用引擎后按需绑定 YAML；宿主可替换配置端口，无配置数据库依赖。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@EnableConfigurationProperties
public class AiApplicationConfigAutoConfiguration {

    /** @return 部署级配置，沿用现有 YAML 前缀与按模式使用时校验的行为 */
    @Bean
    @ConditionalOnMissingBean
    public AiApplicationsProperties aiApplicationsProperties() {
        return new AiApplicationsProperties();
    }

    /**
     * 提供默认 YAML 配置实现，不选择用户身份或授予工具权限。
     * @param properties 宿主启动时绑定的应用标识与工具标识
     * @return 仅在宿主未实现配置端口时创建的默认适配器
     */
    @Bean
    @ConditionalOnMissingBean(AiApplicationConfigPort.class)
    public YamlApplicationConfigAdapter yamlApplicationConfigAdapter(AiApplicationsProperties properties) {
        return new YamlApplicationConfigAdapter(properties);
    }
}
