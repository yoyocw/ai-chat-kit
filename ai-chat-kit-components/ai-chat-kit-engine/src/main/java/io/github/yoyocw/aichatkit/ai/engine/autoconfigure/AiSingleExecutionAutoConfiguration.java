package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.service.chat.AiSingleChatExecutor;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;

import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatBusinessPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;

/** 单聊嵌入执行装配；宿主必须提供身份/存储/事务/业务/审计合同，缺失时不装配业务引擎。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
// 与 JDBC 事务配置同阶段装配，避免 after 依赖递归将事务配置拉到数据源注册之前。
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@AutoConfigureAfter(value = {AiModelRuntimeAutoConfiguration.class, AiApplicationConfigAutoConfiguration.class},
        name = {"org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration"})
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
public class AiSingleExecutionAutoConfiguration {
    /**
     * 端口齐备时创建单聊引擎，使用宿主事务管理器保持准备/来源/审计的本地事务。
     * 参数均为宿主合同或本地模型组件，不从远程AI代理获取结果。
     * @return 可替换的单聊准备、执行及停止服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AiSingleChatStatePort.class, AiInvocationContextPort.class, AiHostExecutionScopePort.class,
            AiSingleChatCompletionPort.class, AiSingleChatPreparePort.class, AiModelClient.class,
            AiApplicationConfigPort.class, AiExecutionPolicy.class, AiSingleChatBusinessPort.class,
            AiSingleResponseDataPort.class, AiExecutionAuditPort.class, AiMessageOriginPort.class, AiInvocationAuthorizationPort.class,
            AiTransactionExecutor.class})
    public AiSingleChatExecutor aiSingleChatExecutor(AiSingleChatStatePort state, AiInvocationContextPort identity,
            AiHostExecutionScopePort scope, AiSingleChatCompletionPort completion, AiSingleChatPreparePort prepare,
            AiModelClient client, AiApplicationConfigPort config, AiExecutionPolicy properties,
            AiSingleChatBusinessPort business, AiInvocationAuthorizationPort authorization, AiSingleResponseDataPort response,
            AiExecutionAuditPort audit, AiMessageOriginPort origin, AiTransactionExecutor transactions) {
        return new AiSingleChatExecutor(state, identity, scope, completion, prepare, client, config, properties,
                business, authorization, response, audit, origin, transactions);
    }

}
