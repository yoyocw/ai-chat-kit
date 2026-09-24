package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/** 平台普通登录按主体租户绑定的受限认证消费者列表。 */
@ConfigurationProperties(prefix = "ai-chat-kit.ai.platform-host")
public class PlatformHostInspectionProperties {
    private List<PlatformTenantInspectionBinding> inspections = new ArrayList<>();

    public List<PlatformTenantInspectionBinding> getInspections() { return inspections; }
    public void setInspections(List<PlatformTenantInspectionBinding> value) { inspections = value; }
}
