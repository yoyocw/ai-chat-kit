package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.ai.starter.config.AiStarterProperties;
import io.github.yoyocw.aichatkit.ai.starter.host.*;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/** 显式Web开关才装配；没有注解、引擎或完整门面时明确启动失败，不默默缺失路由。 */
@AutoConfiguration(after = AiWebExecutionAutoConfiguration.class,
        afterName = "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration")
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai", name = {"engine.enabled", "web.enabled"}, havingValue = "true")
@EnableConfigurationProperties({AiWebProperties.class, AiStarterProperties.class})
public class AiWebAutoConfiguration {
    /** @return 开启前的严格依赖与最终路由检查器 */
    @Bean
    public AiWebActivation aiWebActivation(ListableBeanFactory beans, Environment environment,
            AiWebProperties web, AiStarterProperties starter) {
        return new AiWebActivation(beans, environment, web, starter);
    }
    /** @return 只读取真实宿主上下文的同步身份捕获 */
    @Bean
    public AiWebIdentity aiWebIdentity(AiWebActivation activation, AiInvocationContextPort context,
            AiStarterProperties starter) {
        return new AiWebIdentity(context, starter.getNamespace());
    }
    /** @return 可选单聊完整路由；全局扫描与显式装配共享相同条件 */
    @Bean
    @Conditional(AiWebSingleCondition.class)
    @ConditionalOnMissingBean(AiWebSingleController.class)
    public AiWebSingleController aiWebSingleController(AiWebActivation activation, AiWebIdentity identity,
            AiHostSingleChatService execution, AiHostConversationManagementService management,
            AiHostConversationShareService shares, AiPublicConversationShareService publicShares,
            AiWebStreamResponseFactory responses) {
        return new AiWebSingleController(activation, identity, execution, management, shares, publicShares, responses);
    }
    /** @return 可选群聊完整路由 */
    @Bean
    @Conditional(AiWebGroupCondition.class)
    @ConditionalOnMissingBean(AiWebGroupController.class)
    public AiWebGroupController aiWebGroupController(AiWebActivation activation, AiWebIdentity identity,
            AiHostGroupChatService execution, AiHostConversationManagementService management,
            AiHostConversationShareService shares, AiPublicConversationShareService publicShares,
            AiHostGroupAgentService agents, AiWebStreamResponseFactory responses) {
        return new AiWebGroupController(activation, identity, execution, management, shares, publicShares, agents, responses);
    }
    /** @return 仅针对本模块的安全异常响应 */
    @Bean
    @ConditionalOnMissingBean(AiWebErrorAdvice.class)
    public AiWebErrorAdvice aiWebErrorAdvice(AiWebActivation activation) {
        return new AiWebErrorAdvice(activation);
    }
}
