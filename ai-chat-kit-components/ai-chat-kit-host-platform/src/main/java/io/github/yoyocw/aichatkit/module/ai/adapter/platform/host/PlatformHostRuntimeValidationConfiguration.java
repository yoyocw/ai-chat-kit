package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 激活普通宿主后确认已解析可信的原生登录桥。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnExpression("'${ai-chat-kit.ai.engine.enabled:false}' == 'true' && "
        + "'${ai-chat-kit.ai.platform-host.mode:}' == 'ordinary-bearer'")
@AutoConfigureBefore(PlatformHostConfiguration.class)
public class PlatformHostRuntimeValidationConfiguration {
    @Bean
    public SmartInitializingSingleton platformHostRuntimeDependencyVerifier(PlatformNativeLoginSource nativeLogin) {
        return () -> nativeLogin.adminUserType();
    }
}
