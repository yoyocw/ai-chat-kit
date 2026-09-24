package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** 仅供启用注解或旧宿主显式导入；不是组件，也不进入自动配置清单。 */
public class AiRuntimeActivationConfiguration {
    /** @return 无副作用的运行时显式选择标记 */
    @Bean
    @ConditionalOnMissingBean(AiRuntimeActivation.class)
    public AiRuntimeActivation aiRuntimeActivation() { return new AiRuntimeActivation(); }
}
