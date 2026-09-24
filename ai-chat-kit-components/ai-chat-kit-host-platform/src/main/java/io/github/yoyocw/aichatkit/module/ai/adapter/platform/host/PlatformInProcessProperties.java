package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 可选的系统租户配置；普通身份仍取当前可信宿主上下文。 */
@ConfigurationProperties(prefix = "ai-chat-kit.ai.platform-host.in-process")
public class PlatformInProcessProperties {
    private Long platformTenantId;
    /** 原宿主包根，仅由可信部署配置提供；不设置默认值。 */
    private String nativePackageRoot;

    public Long getPlatformTenantId() { return platformTenantId; }

    public void setPlatformTenantId(Long platformTenantId) { this.platformTenantId = platformTenantId; }

    public String getNativePackageRoot() { return nativePackageRoot; }

    public void setNativePackageRoot(String nativePackageRoot) { this.nativePackageRoot = nativePackageRoot; }
}
