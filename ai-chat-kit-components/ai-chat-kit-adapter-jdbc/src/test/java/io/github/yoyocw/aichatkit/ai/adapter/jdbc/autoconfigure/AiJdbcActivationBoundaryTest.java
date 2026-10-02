package io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivationConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopOriginPrecheckService;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.mapper.AiConversationMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Spring lifecycle and resource holders; these tests never open a database connection. */
class AiJdbcActivationBoundaryTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiJdbcStorageAutoConfiguration.class,
                    AiJdbcStopOriginAutoConfiguration.class, AiJdbcExecutionScopeAutoConfiguration.class));

    @Test
    void withoutExplicitActivationAllJdbcSwitchesAreIgnoredEvenWithMissingCredentials() {
        runner.withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.storage.type=postgresql",
                        "ai-chat-kit.ai.storage.jdbc.mode=isolated", "ai-chat-kit.ai.execution.scope=jdbc-explicit")
                .run(this::assertJdbcAbsent);
    }

    @Test
    void disabledEngineIgnoresJdbcSwitchesAndDoesNotRequireConfiguration() {
        runner.withUserConfiguration(ExplicitActivation.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false", "ai-chat-kit.ai.storage.type=postgresql",
                        "ai-chat-kit.ai.storage.jdbc.mode=isolated", "ai-chat-kit.ai.execution.scope=jdbc-explicit")
                .run(this::assertJdbcAbsent);
    }

    @Test
    void selectedIsolatedStorageFailsExplicitlyWhenEnabledWithoutCredentials() {
        enabledStorage().withPropertyValues("ai-chat-kit.ai.storage.jdbc.mode=isolated")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("AI JDBC 资源配置无效或数据源与事务管理器不同源"));
    }

    @Test
    void reuseBorrowsHostResourcesWithoutAddingCandidatesOrClosingThem() {
        verifyBorrowedResources("reuse");
    }

    @Test
    void referenceBorrowsNamedHostResourcesWithoutAddingCandidatesOrClosingThem() {
        verifyBorrowedResources("reference");
    }

    @Test
    void isolatedResourcesRemainPrivateAndOnlyTheirPoolClosesWithAiContext() {
        CountingDataSource hostSource = new CountingDataSource();
        CountingTransactionManager hostManager = new CountingTransactionManager(hostSource);
        AtomicReference<HikariDataSource> ownedPool = new AtomicReference<>();
        try (AnnotationConfigApplicationContext host = hostContext(hostSource, hostManager)) {
            isolatedStorage().withParent(host).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(AiJdbcResources.class);
                AiJdbcResources resources = context.getBean(AiJdbcResources.class);
                ownedPool.set((HikariDataSource) resources.source());
                assertThat(ownedPool.get().isClosed()).isFalse();
                assertThat(resources.source()).isNotSameAs(hostSource);
                assertThat(resources.manager()).isNotSameAs(hostManager);
                assertNoChildGlobalCandidates(context);
                assertThat(hostSource.connectionCalls).isZero();
            });
            assertThat(ownedPool.get()).isNotNull();
            assertThat(ownedPool.get().isClosed()).isTrue();
            assertHostStillOpen(host, hostSource, hostManager);
        }
        assertThat(hostSource.closeCalls).isEqualTo(1);
        assertThat(hostManager.closeCalls).isEqualTo(1);
    }

    @Test
    void failedRefreshClosesAlreadyCreatedIsolatedPool() {
        AtomicReference<HikariDataSource> ownedPool = new AtomicReference<>();
        isolatedStorage().withPropertyValues("ai-chat-kit.ai.starter.namespace=")
                .withInitializer(context -> context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                    @Override
                    public Object postProcessAfterInitialization(Object bean, String beanName) {
                        if (bean instanceof AiJdbcResources) {
                            ownedPool.set((HikariDataSource) ((AiJdbcResources) bean).source());
                        }
                        return bean;
                    }
                }))
                .run(context -> {
                    assertThat(context).hasFailed().getFailure()
                            .hasStackTraceContaining("AI PostgreSQL 适配需要同一数据源的 JDBC 事务管理器及有效命名空间");
                    assertThat(ownedPool.get()).isNotNull();
                    assertThat(ownedPool.get().isClosed()).isTrue();
                });
    }

    private ApplicationContextRunner enabledStorage() {
        return runner.withUserConfiguration(ExplicitActivation.class, MemoryService.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=true", "ai-chat-kit.ai.storage.type=postgresql",
                        "ai-chat-kit.ai.starter.namespace=activation-test");
    }

    private ApplicationContextRunner isolatedStorage() {
        // Hikari is lazily initialized. The reserved invalid hostname must never be contacted.
        return enabledStorage().withPropertyValues("ai-chat-kit.ai.storage.jdbc.mode=isolated",
                "ai-chat-kit.ai.storage.jdbc.jdbc-url=jdbc:postgresql://must-not-connect.invalid/ai",
                "ai-chat-kit.ai.storage.jdbc.username=test-only", "ai-chat-kit.ai.storage.jdbc.password=test-only");
    }

    private void verifyBorrowedResources(String mode) {
        CountingDataSource hostSource = new CountingDataSource();
        CountingTransactionManager hostManager = new CountingTransactionManager(hostSource);
        try (AnnotationConfigApplicationContext host = hostContext(hostSource, hostManager)) {
            ApplicationContextRunner configured = enabledStorage().withParent(host)
                    .withPropertyValues("ai-chat-kit.ai.storage.jdbc.mode=" + mode);
            if ("reference".equals(mode)) {
                configured = configured.withPropertyValues("ai-chat-kit.ai.storage.jdbc.data-source-bean=hostDataSource",
                        "ai-chat-kit.ai.storage.jdbc.transaction-manager-bean=hostTransactionManager");
            }
            configured.run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(AiJdbcResources.class);
                AiJdbcResources resources = context.getBean(AiJdbcResources.class);
                assertThat(resources.source()).isSameAs(hostSource);
                assertThat(resources.manager()).isSameAs(hostManager);
                assertThat(context.getBean(AiTransactionExecutor.class).manager()).isSameAs(hostManager);
                assertNoChildGlobalCandidates(context);
                assertThat(hostSource.connectionCalls).isZero();
            });
            // Child close executes the actual AiJdbcResources.close() via Spring's destroy callback.
            assertHostStillOpen(host, hostSource, hostManager);
        }
        // Only the owning parent context may close these resources, exactly once.
        assertThat(hostSource.closeCalls).isEqualTo(1);
        assertThat(hostManager.closeCalls).isEqualTo(1);
        assertThat(hostSource.connectionCalls).isZero();
    }

    private AnnotationConfigApplicationContext hostContext(CountingDataSource source,
                                                          CountingTransactionManager manager) {
        AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
        host.registerBean("hostDataSource", CountingDataSource.class, () -> source,
                definition -> definition.setDestroyMethodName("close"));
        host.registerBean("hostTransactionManager", CountingTransactionManager.class, () -> manager,
                definition -> definition.setDestroyMethodName("close"));
        host.refresh();
        return host;
    }

    private void assertHostStillOpen(AnnotationConfigApplicationContext host, CountingDataSource source,
                                    CountingTransactionManager manager) {
        assertThat(host.isActive()).isTrue();
        assertThat(host.getBeansOfType(DataSource.class)).hasSize(1).containsValue(source);
        assertThat(host.getBeansOfType(PlatformTransactionManager.class)).hasSize(1).containsValue(manager);
        assertThat(source.closeCalls).isZero();
        assertThat(manager.closeCalls).isZero();
        assertThat(source.connectionCalls).isZero();
    }

    private void assertNoChildGlobalCandidates(AssertableApplicationContext context) {
        assertThat(context.getSourceApplicationContext().getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getSourceApplicationContext().getBeansOfType(PlatformTransactionManager.class)).isEmpty();
        assertThat(context.getSourceApplicationContext().getBeansOfType(SqlSessionFactory.class)).isEmpty();
        assertThat(context.getSourceApplicationContext().getBeansOfType(SqlSessionTemplate.class)).isEmpty();
        assertThat(context.getSourceApplicationContext().getBeansOfType(AiConversationMapper.class)).isEmpty();
    }

    private void assertJdbcAbsent(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().doesNotHaveBean(AiJdbcResources.class)
                .doesNotHaveBean(AiJdbcAccess.class).doesNotHaveBean(AiTransactionExecutor.class)
                .doesNotHaveBean(AiHostExecutionScopePort.class).doesNotHaveBean(AiStopOriginPrecheckService.class)
                .doesNotHaveBean(DataSource.class).doesNotHaveBean(PlatformTransactionManager.class);
    }

    // JDBC depends on engine, not starter. Import the same marker configuration used by @EnableAiChatKit.
    @Configuration(proxyBeanMethods = false)
    @Import(AiRuntimeActivationConfiguration.class)
    static class ExplicitActivation { }

    @Configuration(proxyBeanMethods = false)
    static class MemoryService {
        @Bean
        AiConversationMemoryService memory() { return new AiConversationMemoryService(new BailianProperties()); }
    }

    static final class CountingDataSource extends AbstractDataSource implements AutoCloseable {
        private int connectionCalls;
        private int closeCalls;

        @Override
        public Connection getConnection() throws SQLException {
            connectionCalls++;
            throw new SQLException("These lifecycle tests must not open a database connection");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public void close() { closeCalls++; }
    }

    static final class CountingTransactionManager extends DataSourceTransactionManager implements AutoCloseable {
        private int closeCalls;

        CountingTransactionManager(DataSource source) { super(source); }

        @Override
        public void close() { closeCalls++; }
    }
}
