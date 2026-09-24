package io.github.yoyocw.aichatkit.ai.starter.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 注解专用的 lite Import 配置；不参与组件扫描，不提前创建业务资源。 */
public class AiStarterMarkerConfiguration {
    /** @return 无副作用的显式启用标记，关闭总开关时也不触发业务装配 */
    @Bean
    @ConditionalOnMissingBean(AiStarterMarker.class)
    public AiStarterMarker aiStarterMarker() { return new AiStarterMarker(); }
}
