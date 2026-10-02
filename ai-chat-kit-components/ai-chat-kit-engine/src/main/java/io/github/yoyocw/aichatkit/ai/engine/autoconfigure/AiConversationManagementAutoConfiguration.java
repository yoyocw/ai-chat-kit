package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationStorePort;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationManagementService;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;

/** 管理接口仅在真实身份、存储和事务齐备时装配，不依赖林业服务或分享逻辑。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@AutoConfigureAfter(value = AiModelRuntimeAutoConfiguration.class,
        name = {"io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure.AiJdbcStorageAutoConfiguration",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"})
@AutoConfigureBefore(name = "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration")
public class AiConversationManagementAutoConfiguration {
    /** @return 最后一次身份核对及数据库事务覆盖的管理用例服务 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AiInvocationContextPort.class, AiConversationStorePort.class, AiModelClient.class, AiTransactionExecutor.class})
    public AiConversationManagementService aiConversationManagementService(AiInvocationContextPort identities,
            AiConversationStorePort storage, AiModelClient client, AiTransactionExecutor transactions) {
        return new AiConversationManagementService(identities, storage, client, transactions);
    }
}
