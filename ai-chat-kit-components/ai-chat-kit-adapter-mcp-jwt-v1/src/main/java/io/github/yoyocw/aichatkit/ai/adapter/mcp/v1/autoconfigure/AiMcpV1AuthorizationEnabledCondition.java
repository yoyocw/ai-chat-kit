package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 普通授权新开关或历史签发开关任一显式开启时，装配共享授权服务。 */
final class AiMcpV1AuthorizationEnabledCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean authorization = context.getEnvironment().getProperty(
                "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled", Boolean.class, false);
        boolean signing = context.getEnvironment().getProperty(
                "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled", Boolean.class, false);
        return authorization || signing
                ? ConditionOutcome.match("AI MCP application authorization is explicitly enabled")
                : ConditionOutcome.noMatch("AI MCP application authorization is disabled");
    }
}
