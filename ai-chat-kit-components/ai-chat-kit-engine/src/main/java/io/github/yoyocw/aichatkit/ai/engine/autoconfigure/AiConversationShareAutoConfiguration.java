package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.config.AiShareProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiConversationSharePort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;

/** 分享存储与事务齐备才装配；公开读取不要求创建者登录，创建/撤销仍需真实身份端口。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiShareProperties.class)
@AutoConfigureAfter(name = {"io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure.AiJdbcStorageAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"})
@AutoConfigureBefore(name = "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration")
public class AiConversationShareAutoConfiguration {
    /** 缺身份时只允许公开读；创建/撤销显式失败，不构造匿名创建者身份。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AiConversationSharePort.class, AiTransactionExecutor.class})
    public AiConversationShareService aiConversationShareService(ObjectProvider<AiInvocationContextPort> identities,
            AiConversationSharePort storage, AiTransactionExecutor transactions, AiShareProperties properties) {
        return new AiConversationShareService(identities.getIfAvailable(), storage, transactions, properties);
    }
}
