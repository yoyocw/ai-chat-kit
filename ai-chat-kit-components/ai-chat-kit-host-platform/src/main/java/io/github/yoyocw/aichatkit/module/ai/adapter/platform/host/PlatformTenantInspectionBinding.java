package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

/** 单个平台租户的受限认证消费者绑定；秘密仅由部署配置注入。 */
public class PlatformTenantInspectionBinding {
    private Long tenantId;
    private String baseUrl;
    private String consumerAccessToken;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getConsumerAccessToken() { return consumerAccessToken; }
    public void setConsumerAccessToken(String value) { consumerAccessToken = value; }
}
