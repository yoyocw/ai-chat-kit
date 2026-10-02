package io.github.yoyocw.aichatkit.ai.adapter.jdbc.autoconfigure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 仅显式选择 PostgreSQL 方言时注册持久化，不推断其他数据库兼容性。 */
final class AiPostgreSqlStorageCondition implements Condition {
    /** 配置值按固定字面量匹配，不解释为表达式。 */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return "postgresql".equals(context.getEnvironment().getProperty("ai-chat-kit.ai.storage.type"));
    }
}
