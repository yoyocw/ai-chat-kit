package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/** 旧固定租户复核的独立运行时边界；普通宿主由激活后的验证配置负责。 */
public final class PlatformHostRuntimeCondition extends SpringBootCondition {
    /**
     * 旧 session-inspection 未启用时不要求传输类；显式启用却缺依赖必须失败。
     * @return 依赖满足或未启用时继续正常条件装配
     * @throws IllegalStateException 显式能力缺少所需运行时类型
     */
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean inspection = context.getEnvironment().getProperty("ai-chat-kit.ai.session-inspection.enabled", Boolean.class, false);
        if (inspection) {
            require(context, "io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient");
        }
        return ConditionOutcome.match("旧固定租户复核依赖已满足，或未显式启用");
    }

    /** 固定类型名来自代码，不回显配置或凭据，也不加载替代身份实现。 */
    private void require(ConditionContext context, String type) {
        if (!ClassUtils.isPresent(type, context.getClassLoader())) {
            throw new IllegalStateException("启用平台宿主能力缺少运行时类型：" + type);
        }
    }
}
