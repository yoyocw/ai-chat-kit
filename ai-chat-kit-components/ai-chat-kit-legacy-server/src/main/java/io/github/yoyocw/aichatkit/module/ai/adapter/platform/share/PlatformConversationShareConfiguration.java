package io.github.yoyocw.aichatkit.module.ai.adapter.platform.share;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisConversationShareAdapter;
import io.github.yoyocw.aichatkit.module.ai.config.AiChatProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiShareProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 原林业分享专用装配：保持原表、原事务定义及原URL默认值，不修改全局Primary。 */
@AutoConfiguration(afterName = {
        "io.github.yoyocw.aichatkit.module.ai.adapter.platform.transaction.PlatformTransactionAutoConfiguration",
        "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration"},
        beforeName = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiConversationShareAutoConfiguration")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@ConditionalOnBean({MyBatisConversationShareAdapter.class, AiTransactionExecutor.class})
public class PlatformConversationShareConfiguration {
    /**
     * @param identities 真实林业身份
     * @param storage 原表分享端口
     * @param transactions 与原MyBatis同源的执行器
     * @param legacyDefinition 原宿主事务模板，不改其传播、隔离、超时或只读值
     * @param legacyProperties 已完成原配置绑定和校验，包含旧默认分享URL
     * @return 替代中立默认装配的原表分享用例；缺失或异源事务直接拒绝
     */
    @Bean
    public AiConversationShareService platformConversationShareService(AiInvocationContextPort identities,
            MyBatisConversationShareAdapter storage, AiTransactionExecutor transactions,
            TransactionTemplate legacyDefinition, AiChatProperties legacyProperties) {
        if (legacyDefinition.getTransactionManager() != transactions.manager()) {
            throw new IllegalStateException("原分享事务模板与AI存储不同源");
        }
        // 显式复制旧配置，不能依赖另一Properties是否已绑定或使用空对象丢失默认URL。
        AiShareProperties properties = new AiShareProperties();
        properties.setShareBaseUrl(legacyProperties.getShareBaseUrl());
        properties.setGroupShareBaseUrl(legacyProperties.getGroupShareBaseUrl());
        // 公开读含最终计数，沿用旧@Transactional/required的可写REQUIRED与默认隔离。
        return new AiConversationShareService(identities, storage, transactions, properties,
                legacyDefinition, new DefaultTransactionDefinition());
    }
}
