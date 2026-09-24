package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.ai.starter.config.AiStarterProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 群聊组件和工厂共用条件，宿主扫描也不能绕过开关或模式选择。 */
public final class AiWebGroupCondition implements Condition {
    /** @return 两个显式开关开启且部署选择此模式；其余依赖由激活检查器严格检查 */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!Boolean.TRUE.equals(context.getEnvironment().getProperty("ai-chat-kit.ai.web.enabled", Boolean.class))
                || !Boolean.TRUE.equals(context.getEnvironment().getProperty("ai-chat-kit.ai.engine.enabled", Boolean.class))) {
            return false;
        }
        AiStarterProperties properties = Binder.get(context.getEnvironment())
                .bind("ai-chat-kit.ai.starter", AiStarterProperties.class).orElseGet(AiStarterProperties::new);
        return properties.getModes() != null && properties.getModes().contains(AiChatMode.GROUP);
    }
}

