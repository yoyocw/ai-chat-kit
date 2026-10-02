package io.github.yoyocw.aichatkit.module.ai.adapter.platform.inspection;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 认证服务返回的最小身份快照，不包含原始凭据或权限全集。 */
public class PlatformInspectionResult {
    private Long sessionId;
    private Long userId;
    private Integer userType;
    private Long tenantId;
    private String clientId;
    private Long clientRecordId;
    private Long expiresAtMillis;
    private boolean permissionsSatisfied;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean platformAdministrator;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { sessionId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Integer getUserType() { return userType; }
    public void setUserType(Integer value) { userType = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public String getClientId() { return clientId; }
    public void setClientId(String value) { clientId = value; }
    public Long getClientRecordId() { return clientRecordId; }
    public void setClientRecordId(Long value) { clientRecordId = value; }
    public Long getExpiresAtMillis() { return expiresAtMillis; }
    public void setExpiresAtMillis(Long value) { expiresAtMillis = value; }
    public boolean isPermissionsSatisfied() { return permissionsSatisfied; }
    public void setPermissionsSatisfied(boolean value) { permissionsSatisfied = value; }
    public Boolean getPlatformAdministrator() { return platformAdministrator; }
    public void setPlatformAdministrator(Boolean value) { platformAdministrator = value; }
}
