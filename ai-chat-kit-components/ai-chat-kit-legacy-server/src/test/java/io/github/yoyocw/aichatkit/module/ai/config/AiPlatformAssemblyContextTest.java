package io.github.yoyocw.aichatkit.module.ai.config;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiConversationManagementAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiConversationShareAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiModelRuntimeAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivationConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.management.PlatformConversationManagementBridge;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.share.PlatformConversationShareBridge;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.share.PlatformConversationShareConfiguration;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.transaction.PlatformDataSourceOrderAutoConfiguration;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.transaction.PlatformTransactionAutoConfiguration;
import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisConversationShareAdapter;
import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisConversationStoreAdapter;
import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisGroupChatSupport;
import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisMessageOriginAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.agent.AiAgentMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMemberMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.origin.AiMessageOriginMapper;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatServiceImpl;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationManagementService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatServiceImpl;
import okhttp3.OkHttpClient;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Collections;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 仅验证选定真实配置的容器装配；不扫描完整 server，不执行 SQL、HTTP 或业务用例。
 * 原表适配与桥接真实实例化，发送引擎/身份/Mapper 等本次边界使用 mock。
 */
class AiPlatformAssemblyContextTest {
    /** 每个用例自己的上下文，失败刷新也在 finally 生命周期中关闭。 */
    private AnnotationConfigApplicationContext context;
    /** 本用例的外部及业务边界；启动和关闭均不得调用。 */
    private final List<Object> boundaries = new ArrayList<>();
    /** MyBatis 真实配置绑定的模拟源，不提供可用连接。 */
    private final DataSource source = mock(DataSource.class);
    /** 唯一与上述源同一对象的实际 Spring 事务管理器。 */
    private final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
    /** 原分享定义使用不同于中立默认的参数，检测被错误默认装配覆盖。 */
    private final TransactionTemplate legacy = new TransactionTemplate(manager);

    @AfterEach
    void closeWithoutExternalWork() {
        try {
            if (context != null) { context.close(); }
        } finally {
            verifyNoInteractions(source);
            if (!boundaries.isEmpty()) { verifyNoInteractions(boundaries.toArray()); }
        }
    }

    @Test
    void enabledAssemblesRealAdaptersBridgesAndOldServicesWithOneSameSourceExecutor() throws Exception {
        fullContext();
        // 模拟宿主另一个资源，验证选择靠实际数据源身份而不是全局 Primary。
        DataSource other = mock(DataSource.class); boundaries.add(other);
        context.registerBean("otherManager", DataSourceTransactionManager.class,
                () -> new DataSourceTransactionManager(other));
        context.refresh();
        AiTransactionExecutor executor = context.getBean(AiTransactionExecutor.class);
        assertEquals(1, context.getBeansOfType(AiTransactionExecutor.class).size());
        assertSame(manager, executor.manager()); assertSame(source, executor.resourceFactory());
        assertEquals(1, context.getBeansOfType(AiConversationManagementService.class).size());
        assertEquals(1, context.getBeansOfType(AiConversationShareService.class).size());
        assertTrue(context.containsBean("platformConversationShareService"));
        assertFalse(context.containsBean("aiConversationShareService"));
        assertSame(executor, field(context.getBean(MyBatisConversationStoreAdapter.class), "transactions"));
        assertSame(executor, field(context.getBean(MyBatisConversationShareAdapter.class), "transactions"));
        assertSame(executor, field(context.getBean(MyBatisMessageOriginAdapter.class), "transactions"));
        Object managementBridge = context.getBean(PlatformConversationManagementBridge.class);
        Object shareBridge = context.getBean(PlatformConversationShareBridge.class);
        assertSame(managementBridge, field(context.getBean(AiChatServiceImpl.class), "managementBridge"));
        assertSame(managementBridge, field(context.getBean(AiGroupChatServiceImpl.class), "managementBridge"));
        assertSame(shareBridge, field(context.getBean(AiChatServiceImpl.class), "shareBridge"));
        assertSame(shareBridge, field(context.getBean(AiGroupChatServiceImpl.class), "shareBridge"));
        assertSame(context.getBean(AiConversationManagementService.class), field(managementBridge, "management"));
        assertSame(context.getBean(AiConversationShareService.class), field(shareBridge, "shares"));
        // 真实模型客户端仅建连接池：无凭据、不调用发送，不产生在途请求或连接。
        BailianClient client = context.getBean(BailianClient.class);
        assertFalse(client.isConfigured("0123456789abcdef0123456789abcdef"));
        OkHttpClient http = (OkHttpClient) field(client, "httpClient");
        assertEquals(0, http.dispatcher().runningCallsCount());
        assertEquals(0, http.dispatcher().queuedCallsCount());
        assertEquals(0, http.connectionPool().connectionCount());
    }

    @Test
    void platformSharePreservesBoundUrlsAndCopiesLegacyTransactionDefinition() throws Exception {
        fullContext();
        properties().put("ai-chat-kit.ai.chat.share-base-url", "https://single.example.invalid/share");
        properties().put("ai-chat-kit.ai.chat.group-share-base-url", "https://group.example.invalid/share");
        legacy.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        legacy.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        legacy.setTimeout(17); legacy.setReadOnly(true); legacy.setName("legacy-share");
        context.refresh();
        AiConversationShareService service = context.getBean(AiConversationShareService.class);
        AiShareProperties actual = (AiShareProperties) field(service, "properties");
        assertEquals("https://single.example.invalid/share", actual.baseUrl(AiChatMode.SINGLE));
        assertEquals("https://group.example.invalid/share", actual.baseUrl(AiChatMode.GROUP));
        TransactionDefinition issue = (TransactionDefinition) field(service, "issueDefinition");
        assertNotSame(legacy, issue);
        assertEquals(legacy.getPropagationBehavior(), issue.getPropagationBehavior());
        assertEquals(legacy.getIsolationLevel(), issue.getIsolationLevel());
        assertEquals(17, issue.getTimeout()); assertTrue(issue.isReadOnly());
        assertEquals("legacy-share", issue.getName());
        legacy.setTimeout(29); assertEquals(17, issue.getTimeout());
        TransactionDefinition read = (TransactionDefinition) field(service, "publicReadDefinition");
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED, read.getPropagationBehavior());
        assertEquals(TransactionDefinition.ISOLATION_DEFAULT, read.getIsolationLevel());
        assertEquals(TransactionDefinition.TIMEOUT_DEFAULT, read.getTimeout()); assertFalse(read.isReadOnly());
    }

    @Test
    void platformShareRetainsOriginalDefaultUrls() throws Exception {
        fullContext(); context.refresh();
        AiShareProperties actual = (AiShareProperties) field(context.getBean(AiConversationShareService.class), "properties");
        assertEquals("http://localhost:48080/admin-api/ai/chat/share", actual.baseUrl(AiChatMode.SINGLE));
        assertEquals("http://localhost:48080/admin-api/ai/group-chat/share", actual.baseUrl(AiChatMode.GROUP));
    }

    @Test
    void foreignLegacyTemplateFailsInsteadOfFallingBackToNeutralShare() {
        fullContext();
        DataSource other = mock(DataSource.class); boundaries.add(other);
        legacy.setTransactionManager(new DataSourceTransactionManager(other));
        assertFailure("原分享事务模板与AI存储不同源");
    }

    @Test
    void missingSameSourceManagerFailsWithBindingDiagnostic() {
        fullContext(); context.removeBeanDefinition("hostManager");
        assertFailure("林业AI需要唯一且与MyBatis实际数据源相同的事务绑定");
    }

    @Test
    void missingMyBatisEnvironmentFailsWithBindingDiagnostic() {
        fullContext();
        SqlSessionFactory sessions = context.getDefaultListableBeanFactory().getBean(SqlSessionFactory.class);
        sessions.getConfiguration().setEnvironment(null);
        assertFailure("林业AI需要唯一且与MyBatis实际数据源相同的事务绑定");
    }

    @Test
    void missingIdentityCannotAssembleRequiredPlatformBridges() {
        fullContext(); context.removeBeanDefinition(AiInvocationContextPort.class.getName());
        assertFalse(context.containsBeanDefinition(AiInvocationContextPort.class.getName()));
        // 缺身份使管理用例条件不成立，真实桥接首先报告管理服务缺失。
        assertFailure("AiConversationManagementService");
    }

    @Test
    void missingSqlSessionFactoryDoesNotInventExecutorForOldServices() {
        fullContext(); context.removeBeanDefinition("sessions");
        assertFailure("AiTransactionExecutor");
    }

    @Test
    void explicitlyDisabledOptionalConfigurationsDoNotCreateUseCases() {
        optionalContext(); properties().put("ai-chat-kit.ai.engine.enabled", "false");
        context.refresh(); assertOptionalAbsent();
    }

    @Test
    void absentEngineSwitchLeavesOptionalConfigurationsOff() {
        optionalContext(); context.refresh(); assertOptionalAbsent();
    }

    @Test
    void dynamicDataSourcePrecedesBootManagerWithoutHandRegisteredTransactionBeans() throws Exception {
        DynamicDataSourceProvider provider = dynamicContext();
        context.refresh();
        DynamicRoutingDataSource routing = context.getBean(DynamicRoutingDataSource.class);
        PlatformTransactionManager actual = context.getBean(PlatformTransactionManager.class);
        assertEquals(1, context.getBeansOfType(PlatformTransactionManager.class).size());
        assertTrue(actual instanceof DataSourceTransactionManager);
        assertSame(routing, ((DataSourceTransactionManager) actual).getDataSource());
        assertSame(routing, context.getBean(SqlSessionFactory.class).getConfiguration().getEnvironment().getDataSource());
        AiTransactionExecutor executor = context.getBean(AiTransactionExecutor.class);
        assertSame(actual, executor.manager()); assertSame(routing, executor.resourceFactory());
        assertSame(actual, context.getBean(TransactionTemplate.class).getTransactionManager());
        assertTrue(context.containsBean("platformConversationShareService"));
        assertNotNull(context.getBean(AiConversationManagementService.class));
        assertNotNull(context.getBean(AiChatServiceImpl.class));
        assertNotNull(context.getBean(AiGroupChatServiceImpl.class));
        assertSame(source, routing.getDataSources().get("master"));
        verify(provider, times(1)).loadDataSources();
        verifyNoMoreInteractions(provider);
    }

    /** 真实Dynamic/Boot事务配置，底层源由mock provider提供；不预注册manager或template。 */
    private DynamicDataSourceProvider dynamicContext() {
        fullContext();
        context.removeBeanDefinition("hostManager");
        context.removeBeanDefinition("legacyTemplate");
        context.removeBeanDefinition("sessions");
        properties().put("spring.datasource.dynamic.enabled", "true");
        properties().put("spring.datasource.dynamic.primary", "master");
        properties().put("spring.datasource.dynamic.strict", "true");
        DynamicDataSourceProvider provider = mock(DynamicDataSourceProvider.class);
        when(provider.loadDataSources()).thenReturn(Collections.singletonMap("master", source));
        context.registerBean("testDynamicProvider", DynamicDataSourceProvider.class, () -> provider);
        // 只替换SQL执行边界，配置所引用的DataSource必须是容器实际生成的routing对象。
        context.registerBean("sessions", SqlSessionFactory.class, () -> {
            SqlSessionFactory sessions = mock(SqlSessionFactory.class);
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setEnvironment(new Environment("dynamic-assembly-test", new JdbcTransactionFactory(),
                    context.getBean(DataSource.class)));
            when(sessions.getConfiguration()).thenReturn(configuration);
            return sessions;
        });
        context.register(AiPlatformDynamicAssemblyTestImports.class);
        return provider;
    }

    /** 不扫描完整server；只导入受测自动配置，由Boot导入选择器执行排序。 */
    private void optionalContext() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("assembly-test", new LinkedHashMap<>()));
        context.register(AiPlatformAssemblyTestImports.class);
    }

    /** 装配真实旧表适配、桥接和两个旧服务；未纳入的业务执行链显式模拟。 */
    private void fullContext() {
        optionalContext(); properties().put("ai-chat-kit.ai.engine.enabled", "true");
        context.registerBean("hostManager", DataSourceTransactionManager.class, () -> manager);
        context.registerBean("legacyTemplate", TransactionTemplate.class, () -> legacy);
        SqlSessionFactory sessions = mock(SqlSessionFactory.class);
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setEnvironment(new Environment("assembly-test", new JdbcTransactionFactory(), source));
        when(sessions.getConfiguration()).thenReturn(configuration);
        context.registerBean("sessions", SqlSessionFactory.class, () -> sessions);
        mockBoundary(AiChatConversationMapper.class); mockBoundary(AiChatMessageMapper.class);
        mockBoundary(AiGroupChatConversationMapper.class); mockBoundary(AiGroupChatMessageMapper.class);
        mockBoundary(AiGroupChatMemberMapper.class); mockBoundary(AiAgentMapper.class);
        mockBoundary(AiMessageOriginMapper.class); mockBoundary(AiOriginContextPort.class);
        mockBoundary(AiInvocationContextPort.class); mockBoundary(AiExecutionAuditPort.class);
        mockBoundary(AiGroupChatPreparePort.class); mockBoundary(AiChatExecutionService.class);
        mockBoundary(AiGroupChatExecutionService.class);
        context.register(AiChatProperties.class, MyBatisMessageOriginAdapter.class, MyBatisGroupChatSupport.class,
                MyBatisConversationStoreAdapter.class, MyBatisConversationShareAdapter.class,
                PlatformConversationManagementBridge.class, PlatformConversationShareBridge.class,
                AiChatServiceImpl.class, AiGroupChatServiceImpl.class);
    }

    /** 所有模拟边界均记录，统一断言初始化和销毁没有触发业务。 */
    private <T> void mockBoundary(Class<T> type) {
        T boundary = mock(type); boundaries.add(boundary);
        context.registerBean(type.getName(), type, () -> boundary);
    }

    /** 测试自有属性最高优先级；不载入application配置、真实凭据或外部地址。 */
    private Map<String, Object> properties() {
        return ((MapPropertySource) context.getEnvironment().getPropertySources().get("assembly-test")).getSource();
    }

    /** 校验完整因果链中的受测诊断，不依赖Spring异常外层包装层数。 */
    private void assertFailure(String expected) {
        BeansException failure = assertThrows(BeansException.class, () -> context.refresh());
        StringBuilder chain = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            chain.append(cause.getMessage()).append('\n');
        }
        assertTrue(chain.toString().contains(expected), chain.toString());
    }

    /** 关闭仅针对自动配置子集；不等价于完整旧server能够关闭engine启动。 */
    private void assertOptionalAbsent() {
        assertTrue(context.getBeansOfType(AiTransactionExecutor.class).isEmpty());
        assertTrue(context.getBeansOfType(AiConversationManagementService.class).isEmpty());
        assertTrue(context.getBeansOfType(AiConversationShareService.class).isEmpty());
        assertTrue(context.getBeansOfType(BailianClient.class).isEmpty());
    }

    /** 只读取装配引用/定义快照，避免为验证接线调用真实业务或新增生产getter。 */
    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

/** 刻意逆序列出配置；实际顺序必须由Boot的before/after元数据决定，无测试内类。 */
@Configuration(proxyBeanMethods = false)
@Import(AiRuntimeActivationConfiguration.class)
@ImportAutoConfiguration({AiConversationShareAutoConfiguration.class, AiConversationManagementAutoConfiguration.class,
        PlatformConversationShareConfiguration.class, AiModelRuntimeAutoConfiguration.class,
        PlatformTransactionAutoConfiguration.class})
class AiPlatformAssemblyTestImports {
}

/** 引入真实动态数据源与Boot事务配置；不引入Druid自动数据源或完整server扫描。 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({TransactionAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        DataSourceAutoConfiguration.class, DynamicDataSourceAutoConfiguration.class, PlatformDataSourceOrderAutoConfiguration.class})
class AiPlatformDynamicAssemblyTestImports {
}
