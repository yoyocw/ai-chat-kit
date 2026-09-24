package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisMessageOriginAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopOriginPrecheckService;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** 旧v1与可选v2共用的显式林业预检装配，真实比较MyBatis和事务管理器的数据源。 */
@AutoConfiguration(afterName = {"io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"},
        beforeName = "io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.autoconfigure.AiHostedProxyStopAutoConfiguration")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
public class PlatformStopOriginAutoConfiguration {
    /** @return 同一真实MyBatis数据源的预检，数字/opaque来源仅在林业边界转换 */
    @Bean
    @ConditionalOnBean({MyBatisMessageOriginAdapter.class, SqlSessionFactory.class, AiTransactionExecutor.class})
    @ConditionalOnMissingBean(AiStopOriginPrecheckService.class)
    public AiStopOriginPrecheckService platformStopOriginPrecheckService(ListableBeanFactory factory) {
        AiTransactionExecutor transactions = unique(factory, AiTransactionExecutor.class);
        PlatformTransactionManager manager = transactions.manager();
        SqlSessionFactory sessions = unique(factory, SqlSessionFactory.class);
        AiMessageOriginPort origin = unique(factory, AiMessageOriginPort.class);
        // 唯一性只是避免候选歧义，下面的实际对象比较才证明默认MyBatis事务同源。
        if (!(manager instanceof DataSourceTransactionManager) || sessions.getConfiguration().getEnvironment() == null
                || ((DataSourceTransactionManager) manager).getDataSource()
                != sessions.getConfiguration().getEnvironment().getDataSource()) { throw PlatformV2StopSupport.failure(); }
        return new AiStopOriginPrecheckService(new PlatformStopOriginAdapter(origin), transactions);
    }

    /** 多个事务域或来源端口需由宿主显式提供预检Bean，不用Primary猜测。 */
    private <T> T unique(ListableBeanFactory factory, Class<T> type) {
        String[] names = factory.getBeanNamesForType(type, true, false);
        if (names.length != 1) { throw new IllegalStateException("林业来源预检依赖缺失或不唯一：" + type.getSimpleName()); }
        return factory.getBean(names[0], type);
    }
}
