package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatStreamStatePort;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianGroupOutputParser;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamService;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparePort;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 本进程群聊流式编排；宿主提供数据/身份/展示/审计端口，不引入宿主Mapper和DO。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(AiRuntimeActivation.class)
@AutoConfigureAfter(value = {AiModelRuntimeAutoConfiguration.class, AiApplicationConfigAutoConfiguration.class},
        name = {"io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure.AiJdbcStorageAutoConfiguration",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"})
@EnableTransactionManagement
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
public class AiGroupExecutionAutoConfiguration {
    /** 目录读取独立于模型执行，真实身份及目录齐备才装配。 */
    @Bean
    @ConditionalOnMissingBean(io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupAgentCatalogService.class)
    @ConditionalOnBean({AiInvocationContextPort.class, io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort.class})
    public io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupAgentCatalogService aiGroupAgentCatalogService(
            AiInvocationContextPort identities, io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort catalog) {
        return new io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupAgentCatalogService(identities, catalog);
    }
    /**
     * 仅宿主端口齐备时装配群聊引擎，独立模型调用样例不必提供业务数据库。
     * @param scope 异步身份隔离 @param statePort 群聊存储及事务
     * @param client 本地模型客户端 @param parser 结果解析
     * @param responsePort 宿主展示协议 @param audit 宿主审计
     * @return 本地群聊流执行服务，宿主仍负责同步权限及创建占位
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AiHostExecutionScopePort.class, AiGroupChatStreamStatePort.class, BailianClient.class,
            BailianGroupOutputParser.class, AiGroupResponseDataPort.class, AiExecutionAuditPort.class})
    public AiGroupChatStreamService aiGroupChatStreamService(AiHostExecutionScopePort scope,
            AiGroupChatStreamStatePort statePort, BailianClient client, BailianGroupOutputParser parser,
            AiGroupResponseDataPort responsePort, AiExecutionAuditPort audit) {
        return new AiGroupChatStreamService(scope, statePort, client, parser, responsePort, audit);
    }

    /** 完整同步执行需要真实身份、应用授权、准备存储、来源、审计及同源事务，缺一不装配。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AiInvocationContextPort.class, AiApplicationConfigPort.class, AiInvocationAuthorizationPort.class,
            AiGroupChatPreparePort.class, AiGroupChatStreamService.class, BailianClient.class, BailianProperties.class,
            AiExecutionAuditPort.class, AiMessageOriginPort.class, AiTransactionExecutor.class})
    public AiGroupChatExecutionService aiGroupChatExecutionService(AiInvocationContextPort identities,
            AiApplicationConfigPort applications, AiInvocationAuthorizationPort authorization, AiGroupChatPreparePort preparation,
            AiGroupChatStreamService stream, BailianClient client, BailianProperties properties, AiExecutionAuditPort audit,
            AiMessageOriginPort origins, AiTransactionExecutor transactions) {
        return new AiGroupChatExecutionService(identities, applications, authorization, preparation, stream, client, properties, audit, origins, transactions);
    }
}
