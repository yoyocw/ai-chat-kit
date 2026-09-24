package io.github.yoyocw.aichatkit.module.ai.adapter.platform.transaction;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisMessageOriginAdapter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

/** 显式绑定旧林业MyBatis实际资源，不修改宿主Primary或默认事务选择。 */
@AutoConfiguration(afterName = {"com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration"},
        beforeName = {"io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
                "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
                "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiConversationManagementAutoConfiguration",
                "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiConversationShareAutoConfiguration",
                "io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy.PlatformStopOriginAutoConfiguration"})
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@ConditionalOnBean({SqlSessionFactory.class, MyBatisMessageOriginAdapter.class})
public class PlatformTransactionAutoConfiguration {
    /** @param beans 实际宿主容器 @return 唯一对应AI MyBatis数据源的事务能力，保留同源REQUIRED */
    @Bean
    @ConditionalOnMissingBean(AiTransactionExecutor.class)
    public AiTransactionExecutor platformAiTransactionExecutor(ListableBeanFactory beans) {
        String[] factories = beans.getBeanNamesForType(SqlSessionFactory.class, true, false);
        if (factories.length != 1) { throw invalid(); }
        SqlSessionFactory sessions = beans.getBean(factories[0], SqlSessionFactory.class);
        if (sessions.getConfiguration().getEnvironment() == null) { throw invalid(); }
        DataSource source = sessions.getConfiguration().getEnvironment().getDataSource();
        PlatformTransactionManager selected = null;
        for (PlatformTransactionManager candidate : beans.getBeansOfType(PlatformTransactionManager.class).values()) {
            if (candidate instanceof DataSourceTransactionManager
                    && ((DataSourceTransactionManager) candidate).getDataSource() == source) {
                if (selected != null && selected != candidate) { throw invalid(); }
                selected = candidate;
            }
        }
        if (selected == null || source == null) { throw invalid(); }
        // 只有这里已核对实际DataSource的SqlSessionFactory可作为同源附属资源；
        // 不把其它数据源、事务管理器或任意MyBatis对象加入首轮同步归属白名单。
        return new AiTransactionExecutor(selected, AiTransactionMode.REUSE_HOST,
                java.util.Collections.<Object>singleton(sessions));
    }
    /** 不输出连接参数；复杂多SqlSessionFactory宿主须显式提供自己的事务绑定。 */
    private IllegalStateException invalid() {
        return new IllegalStateException("林业AI需要唯一且与MyBatis实际数据源相同的事务绑定");
    }
}
