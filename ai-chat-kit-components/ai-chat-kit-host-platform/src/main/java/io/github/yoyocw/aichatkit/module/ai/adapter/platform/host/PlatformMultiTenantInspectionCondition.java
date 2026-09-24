package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import java.util.List;

/** 任一多租户绑定出现就进入严格构造校验，不能因首项缺 tenant-id 回退 legacy。 */
final class PlatformMultiTenantInspectionCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        List<PlatformTenantInspectionBinding> bindings = Binder.get(context.getEnvironment())
                .bind("ai-chat-kit.ai.platform-host.inspections",
                        Bindable.listOf(PlatformTenantInspectionBinding.class))
                .orElse(java.util.Collections.emptyList());
        return bindings.isEmpty()
                ? ConditionOutcome.noMatch("No Platform multi-tenant inspection binding configured")
                : ConditionOutcome.match("Platform multi-tenant inspection bindings configured");
    }
}
