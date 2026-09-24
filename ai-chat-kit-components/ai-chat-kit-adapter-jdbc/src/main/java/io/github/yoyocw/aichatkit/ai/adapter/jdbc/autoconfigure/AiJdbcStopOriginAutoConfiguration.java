package io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcMessageOriginAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopOriginPrecheckService;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 默认JDBC来源预检工厂，同源检查复用已构造AiJdbcAccess，不让engine反向识别持久化实现。 */
@AutoConfiguration(after = AiJdbcStorageAutoConfiguration.class,
        beforeName = "io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.autoconfigure.AiHostedProxyStopAutoConfiguration")
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
public class AiJdbcStopOriginAutoConfiguration {
    /** @return 使用默认来源同一Access事务管理器的预检；其它宿主必须显式提供自己的服务 */
    @Bean
    @ConditionalOnBean({AiJdbcMessageOriginAdapter.class, AiJdbcAccess.class})
    @ConditionalOnMissingBean(AiStopOriginPrecheckService.class)
    public AiStopOriginPrecheckService aiJdbcStopOriginPrecheckService(ListableBeanFactory factory) {
        AiJdbcAccess access = unique(factory, AiJdbcAccess.class);
        return new AiStopOriginPrecheckService(unique(factory, AiMessageOriginPort.class), access.executor());
    }

    /** 仅唯一候选可使用默认装配，复杂数据源由宿主显式绑定，不靠Primary猜测。 */
    private <T> T unique(ListableBeanFactory factory, Class<T> type) {
        String[] names = factory.getBeanNamesForType(type, true, false);
        if (names.length != 1) { throw new IllegalStateException("JDBC来源预检依赖缺失或不唯一：" + type.getSimpleName()); }
        return factory.getBean(names[0], type);
    }
}
