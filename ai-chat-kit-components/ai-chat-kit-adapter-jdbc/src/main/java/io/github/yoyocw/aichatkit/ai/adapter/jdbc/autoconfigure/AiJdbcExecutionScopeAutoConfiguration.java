package io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcExecutionAuditAdapter;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcExecutionScopeAdapter;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcGroupChatAdapter;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage.AiJdbcSingleChatAdapter;
import io.github.yoyocw.aichatkit.module.ai.adapter.plain.AiPlainGroupResponseDataAdapter;
import io.github.yoyocw.aichatkit.module.ai.adapter.plain.AiPlainSingleResponseDataAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatStreamStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStatePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 显式 ai-chat-kit.ai.execution.scope=jdbc-explicit 才启用，缺省不补作用域。
 * 宿主须保证实际数据源、模型扩展及 AOP 不依赖线程身份；类型检查不是线程安全证明。
 * 自定义 scope 优先。替换异步端口时必须提供自定义 scope，不自动解包或信任代理。
 * 宿主通过自动配置提供 scope 时，应声明在本配置之前装配。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai", name = {"engine.enabled"}, havingValue = "true")
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${ai-chat-kit.ai.execution.scope:}' == 'jdbc-explicit'")
@ConditionalOnMissingBean(AiHostExecutionScopePort.class)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@AutoConfigureAfter(name = {"io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure.AiJdbcStorageAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiPlainChatAutoConfiguration"})
@AutoConfigureBefore(name = {"io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration"})
public class AiJdbcExecutionScopeAutoConfiguration {
    /**
     * 按引擎相同的单值注入规则选择端口，检查实际候选，歧义直接失败。
     * @return 内置显式 SQL 组合的 scope；不完整或自定义异步组合明确拒绝
     */
    @Bean
    public AiHostExecutionScopePort aiJdbcExecutionScope(AiJdbcAccess access,
            ObjectProvider<AiSingleChatStatePort> states, ObjectProvider<AiSingleChatCompletionPort> completions,
            ObjectProvider<AiGroupChatStreamStatePort> groups, AiExecutionAuditPort audit,
            ObjectProvider<AiSingleResponseDataPort> singleResponses,
            ObjectProvider<AiGroupResponseDataPort> groupResponses) {
        AiSingleChatStatePort single = states.getIfAvailable();
        AiSingleChatCompletionPort completion = completions.getIfAvailable();
        AiGroupChatStreamStatePort group = groups.getIfAvailable();
        requireBuiltin(audit, AiJdbcExecutionAuditAdapter.class);
        if (!((AiJdbcExecutionAuditAdapter) audit).usesAccess(access)) { throw incompatible(); }
        if (single != null || completion != null) {
            requireBuiltin(single, AiJdbcSingleChatAdapter.class);
            if (!((AiJdbcSingleChatAdapter) single).usesAccess(access)) { throw incompatible(); }
            if (single != completion) { throw incompatible(); }
            requireBuiltin(singleResponses.getIfAvailable(), AiPlainSingleResponseDataAdapter.class);
        }
        if (group != null) {
            requireBuiltin(group, AiJdbcGroupChatAdapter.class);
            if (!((AiJdbcGroupChatAdapter) group).usesAccess(access)) { throw incompatible(); }
            requireBuiltin(groupResponses.getIfAvailable(), AiPlainGroupResponseDataAdapter.class);
        }
        if (single == null && group == null) { throw incompatible(); }
        return new AiJdbcExecutionScopeAdapter(access);
    }

    /** 只接受已核对的内置对象；代理和自定义实现需由宿主声明自己的作用域。 */
    private static void requireBuiltin(Object selected, Class<?> expected) {
        if (selected == null || selected.getClass() != expected) { throw incompatible(); }
    }

    /** @return 固定接入错误，不包含身份或配置机密 */
    private static IllegalStateException incompatible() {
        return new IllegalStateException("jdbc-explicit 仅支持内置 JDBC 状态/完成/审计与纯聊天展示组合；请提供自定义 AiHostExecutionScopePort");
    }
}
