package io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiMybatisSession;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResourceProperties;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResourceMode;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcGroupRepository;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcConversationRepository;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcShareRepository;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcConversationShareAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiConversationSharePort;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcConversationStoreAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationStorePort;
import org.springframework.beans.factory.ObjectProvider;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcGroupChatAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatStreamStatePort;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcSingleChatAdapter;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcExecutionAuditAdapter;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcMessageOriginAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStatePort;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/** 显式选择 PostgreSQL 时装配真实存储；不执行初始化 SQL，不扫描林业业务类。 */
@Configuration(proxyBeanMethods = false)
@Conditional(AiPostgreSqlStorageCondition.class)
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiJdbcResourceProperties.class)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@AutoConfigureAfter(name = {"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiModelRuntimeAutoConfiguration"})
@AutoConfigureBefore(name = {"io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration"})
public class AiJdbcStorageAutoConfiguration {
    /** 缺省只创建所选数据源的真实 JDBC 事务管理器，不替换宿主已有事务配置。 */
    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    @ConditionalOnProperty(prefix = "ai-chat-kit.ai.storage.jdbc", name = "mode", havingValue = "reuse", matchIfMissing = true)
    @ConditionalOnSingleCandidate(DataSource.class)
    public PlatformTransactionManager aiJdbcTransactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }

    /** 资源只通过持有器接线；独立池和管理器不成为宿主全局候选。 */
    @Bean(destroyMethod = "close")
    public AiJdbcResources aiJdbcResources(AiJdbcResourceProperties properties, ListableBeanFactory beans,
            ObjectProvider<DataSource> sources, ObjectProvider<PlatformTransactionManager> managers) {
        if (properties.getMode() == AiJdbcResourceMode.ISOLATED) { return AiJdbcResources.isolated(properties); }
        if (properties.getJdbcUrl() != null || properties.getUsername() != null || properties.getPassword() != null) {
            throw new IllegalStateException("仅 isolated 模式允许配置 AI JDBC 连接凭据");
        }
        if (properties.getMode() == AiJdbcResourceMode.REFERENCE) {
            return new AiJdbcResources(named(beans, properties.getDataSourceBean(), DataSource.class),
                    named(beans, properties.getTransactionManagerBean(), PlatformTransactionManager.class));
        }
        if (properties.getMode() != AiJdbcResourceMode.REUSE || properties.getDataSourceBean() != null
                || properties.getTransactionManagerBean() != null) {
            throw new IllegalStateException("AI JDBC 资源选择配置无效");
        }
        return new AiJdbcResources(sources.getIfAvailable(), managers.getIfAvailable());
    }

    /** 显式 Bean 名来自部署配置，缺失或类型错误拒绝，不回退宿主 Primary。 */
    private <T> T named(ListableBeanFactory beans, String name, Class<T> type) {
        if (name == null || name.trim().isEmpty() || !name.equals(name.trim())
                || !beans.containsBean(name) || !beans.isTypeMatch(name, type)) {
            throw new IllegalStateException("AI JDBC 显式资源引用缺失或类型不符");
        }
        return beans.getBean(name, type);
    }

    /** 内部会话工厂不成为宿主全局 MyBatis 候选，也不接管宿主的插件和 Mapper。 */
    @Bean
    public AiMybatisSession aiMybatisSession(AiJdbcResources resources) {
        return new AiMybatisSession(resources.source());
    }

    /** 私有模式仅接受自身生命周期事务；复用和显式引用允许真正同源宿主事务，均拒绝异库外层事务。 */
    @Bean
    public AiTransactionExecutor aiJdbcTransactionExecutor(AiJdbcResources resources, AiJdbcResourceProperties properties, AiMybatisSession sessions) {
        return sessions.executor(resources.manager(), properties.getMode() == AiJdbcResourceMode.ISOLATED
                ? AiTransactionMode.ISOLATED : AiTransactionMode.REUSE_HOST);
    }

    /** 所有存储固定绑定 AI 执行器，防止其他 Primary 隐藏了引擎与存储使用不同事务的错误。 */
    @Bean
    public SmartInitializingSingleton aiJdbcTransactionVerifier(ListableBeanFactory beans, AiJdbcAccess access) {
        return () -> {
            java.util.Map<String, AiTransactionExecutor> candidates = beans.getBeansOfType(AiTransactionExecutor.class);
            if (candidates.size() != 1 || candidates.values().iterator().next() != access.executor()) {
                throw new IllegalStateException("AI JDBC 与引擎必须使用同一个显式事务执行器");
            }
        };
    }

    /** 所有存储固定绑定 AI 选定资源，不再按宿主全局事务候选选取。 */
    @Bean
    public AiJdbcAccess aiJdbcAccess(AiJdbcResources resources, AiTransactionExecutor executor, Environment environment, AiMybatisSession sessions) {
        if (resources.manager() != executor.manager()) { throw new IllegalStateException("AI JDBC 事务执行器与资源管理器不一致"); }
        return new AiJdbcAccess(resources.source(), executor, environment.getProperty("ai-chat-kit.ai.starter.namespace"), sessions);
    }

    /** @return 复用既有记忆算法的 AI 数据访问对象 */
    @Bean
    public AiJdbcChatRepository aiJdbcChatRepository(AiJdbcAccess access, AiConversationMemoryService memory) {
        return new AiJdbcChatRepository(access, memory);
    }

    /** 三个单聊端口一次装配，已有任一宿主实现时不混用两种消息存储。 */
    @Bean
    @ConditionalOnMissingBean({AiSingleChatPreparePort.class, AiSingleChatCompletionPort.class, AiSingleChatStatePort.class})
    public AiJdbcSingleChatAdapter aiJdbcSingleChatAdapter(AiJdbcAccess access, AiJdbcChatRepository repository) {
        return new AiJdbcSingleChatAdapter(access, repository);
    }

    /** 真实成员目录存在时才装配群聊准备，避免空目录冒充完整群聊能力。 */
    @Bean
    @ConditionalOnBean(AiGroupAgentCatalogPort.class)
    public AiJdbcGroupRepository aiJdbcGroupRepository(AiJdbcAccess access, AiJdbcChatRepository chats, AiGroupAgentCatalogPort catalog) {
        return new AiJdbcGroupRepository(access, chats, catalog);
    }

    /** 群聊准备和终态使用同一套存储，不混用宿主部分实现。 */
    @Bean
    @ConditionalOnBean(AiJdbcGroupRepository.class)
    @ConditionalOnMissingBean({AiGroupChatPreparePort.class, AiGroupChatStreamStatePort.class})
    public AiJdbcGroupChatAdapter aiJdbcGroupChatAdapter(AiJdbcAccess access, AiJdbcChatRepository chats, AiJdbcGroupRepository groups) {
        return new AiJdbcGroupChatAdapter(access, chats, groups);
    }

    /** 列表不依赖目录启用状态；只有成员更新才要求真实目录存在。 */
    @Bean
    public AiJdbcConversationRepository aiJdbcConversationRepository(AiJdbcAccess access, AiJdbcChatRepository chats,
            ObjectProvider<AiGroupAgentCatalogPort> catalog) {
        return new AiJdbcConversationRepository(access, chats, catalog.getIfAvailable());
    }

    /** 单群聊复用同一会话管理存储，宿主可整体替换此端口。 */
    @Bean
    @ConditionalOnMissingBean(AiConversationStorePort.class)
    public AiJdbcConversationStoreAdapter aiJdbcConversationStoreAdapter(AiJdbcConversationRepository repository) {
        return new AiJdbcConversationStoreAdapter(repository);
    }

    /** 公开查询固定复用同一Access部署namespace，不额外提供访客可指定的作用域。 */
    @Bean
    public AiJdbcShareRepository aiJdbcShareRepository(AiJdbcAccess access, AiJdbcChatRepository chats) {
        return new AiJdbcShareRepository(access, chats);
    }

    /** 创建/撤销和公开读取共用同一分享记录及有效性条件。 */
    @Bean
    @ConditionalOnMissingBean(AiConversationSharePort.class)
    public AiJdbcConversationShareAdapter aiJdbcConversationShareAdapter(AiJdbcShareRepository repository) {
        return new AiJdbcConversationShareAdapter(repository);
    }

    /** @return 同数据库执行审计实现 */
    @Bean
    @ConditionalOnMissingBean(AiExecutionAuditPort.class)
    public AiJdbcExecutionAuditAdapter aiJdbcExecutionAuditAdapter(AiJdbcAccess access) {
        return new AiJdbcExecutionAuditAdapter(access);
    }

    /** 宿主必须提供可信来源捕获，缺失时保持能力缺口，不能默认为普通请求。 */
    @Bean
    @ConditionalOnBean(AiOriginContextPort.class)
    @ConditionalOnMissingBean(AiMessageOriginPort.class)
    public AiJdbcMessageOriginAdapter aiJdbcMessageOriginAdapter(AiJdbcAccess access, AiOriginContextPort origin) {
        return new AiJdbcMessageOriginAdapter(access, origin);
    }
}
