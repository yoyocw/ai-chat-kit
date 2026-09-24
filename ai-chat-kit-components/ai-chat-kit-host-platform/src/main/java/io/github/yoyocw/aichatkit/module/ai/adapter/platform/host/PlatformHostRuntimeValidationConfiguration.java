package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/** 激活普通 Platform 宿主后再校验 provided 依赖，关闭引擎或缺少启用标记时不触碰平台类。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnExpression("'${ai-chat-kit.ai.engine.enabled:false}' == 'true' && "
        + "'${ai-chat-kit.ai.platform-host.mode:}' == 'ordinary-bearer'")
@AutoConfigureBefore(PlatformHostConfiguration.class)
public class PlatformHostRuntimeValidationConfiguration {
    private static final String[] REQUIRED = {
            "io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient",
            "io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser",
            "io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils",
            "io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder",
            "javax.servlet.http.HttpServletRequest"
    };

    @Bean
    public SmartInitializingSingleton platformHostRuntimeDependencyVerifier(ResourceLoader resources) {
        return () -> {
            // 必须使用宿主上下文可见性；测试/容器可能通过隔离类加载器隐藏 provided 依赖。
            ClassLoader loader = resources.getClassLoader();
            for (String type : REQUIRED) {
                if (!ClassUtils.isPresent(type, loader)) {
                    throw new IllegalStateException("启用平台宿主能力缺少运行时类型：" + type);
                }
            }
        };
    }
}
