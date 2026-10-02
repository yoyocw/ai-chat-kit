package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 旧固定租户复核的独立运行时边界；普通宿主由激活后的验证配置负责。 */
public final class PlatformHostRuntimeCondition extends SpringBootCondition {
    /**
     * 旧 session-inspection 未启用时不要求传输类；显式启用却缺依赖必须失败。
     * @return 依赖满足或未启用时继续正常条件装配
     * @throws IllegalStateException 显式能力缺少所需运行时类型
     */
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return ConditionOutcome.match("受限核验传输由 host-platform 提供");
    }
}
