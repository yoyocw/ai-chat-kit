package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.adapter.plain.AiPlainChatBusinessAdapter;
import io.github.yoyocw.aichatkit.module.ai.adapter.plain.AiPlainSingleResponseDataAdapter;
import io.github.yoyocw.aichatkit.module.ai.adapter.plain.AiPlainGroupResponseDataAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatBusinessPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupResponseDataPort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 纯聊天可选默认适配，不创建身份、会话、权限、授权或异步作用域实现。
 * 普通宿主配置先于此自动配置注册；自定义授权自动配置必须声明在本类之前执行。
 * 缺授权端口保持业务端口缺失，由最终能力诊断明确报错，不自动放行。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@AutoConfigureAfter(AiApplicationConfigAutoConfiguration.class)
@AutoConfigureBefore({AiSingleExecutionAutoConfiguration.class, AiGroupExecutionAutoConfiguration.class})
public class AiPlainChatAutoConfiguration {
    /** 宿主已有业务上下文时不抢占业务桥接，要求宿主提供完整单聊业务适配。 */
    @Bean
    @ConditionalOnBean(AiInvocationAuthorizationPort.class)
    @ConditionalOnMissingBean({AiSingleChatBusinessPort.class, AiBusinessContextPort.class})
    public AiPlainChatBusinessAdapter aiPlainChatBusinessAdapter(AiInvocationAuthorizationPort authorization) {
        return new AiPlainChatBusinessAdapter(authorization);
    }

    /** 独立覆盖单聊展示端口，不因群聊或业务配置存在而覆盖宿主实现。 */
    @Bean
    @ConditionalOnMissingBean(AiSingleResponseDataPort.class)
    public AiPlainSingleResponseDataAdapter aiPlainSingleResponseDataAdapter() {
        return new AiPlainSingleResponseDataAdapter();
    }

    /** 群聊默认仅处理受控汇总字段及已筛选引用，不接受业务地图展示。 */
    @Bean
    @ConditionalOnMissingBean(AiGroupResponseDataPort.class)
    public AiPlainGroupResponseDataAdapter aiPlainGroupResponseDataAdapter() {
        return new AiPlainGroupResponseDataAdapter();
    }
}
