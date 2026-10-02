package io.github.yoyocw.aichatkit.module.ai.api.delegation.dto;

import java.util.List;

/** 管理员维护的服务接入边界；保持既有二进制访问器 ABI。 */
public class AiServiceBindingDTO {
    private String businessSystem;
    private String environment;
    private Long tenantId;
    private List<String> appIds;
    private List<String> userAuthToolIds;
    private List<String> mcpIds;

    public String getBusinessSystem() { return businessSystem; }
    public void setBusinessSystem(String businessSystem) { this.businessSystem = businessSystem; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public List<String> getAppIds() { return appIds; }
    public void setAppIds(List<String> appIds) { this.appIds = appIds; }
    public List<String> getUserAuthToolIds() { return userAuthToolIds; }
    public void setUserAuthToolIds(List<String> userAuthToolIds) { this.userAuthToolIds = userAuthToolIds; }
    public List<String> getMcpIds() { return mcpIds; }
    public void setMcpIds(List<String> mcpIds) { this.mcpIds = mcpIds; }
}
