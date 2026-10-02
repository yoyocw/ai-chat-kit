package io.github.yoyocw.aichatkit.module.ai.adapter.platform.inspection;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/** 仅供可信宿主调用层构造的会话复核条件；不得写入日志。 */
public class PlatformInspectionRequest {
    private String subjectType;
    private String accessToken;
    private Long sessionId;
    private Long expectedTenantId;
    private Long expectedUserId;
    private Long expectedClientRecordId;
    private String requiredScope;
    private String requiredResource;
    private List<String> requiredPermissions = new ArrayList<>();
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean requirePlatformAdministrator;

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String value) { subjectType = value; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String value) { accessToken = value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { sessionId = value; }
    public Long getExpectedTenantId() { return expectedTenantId; }
    public void setExpectedTenantId(Long value) { expectedTenantId = value; }
    public Long getExpectedUserId() { return expectedUserId; }
    public void setExpectedUserId(Long value) { expectedUserId = value; }
    public Long getExpectedClientRecordId() { return expectedClientRecordId; }
    public void setExpectedClientRecordId(Long value) { expectedClientRecordId = value; }
    public String getRequiredScope() { return requiredScope; }
    public void setRequiredScope(String value) { requiredScope = value; }
    public String getRequiredResource() { return requiredResource; }
    public void setRequiredResource(String value) { requiredResource = value; }
    public List<String> getRequiredPermissions() { return requiredPermissions; }
    public void setRequiredPermissions(List<String> value) { requiredPermissions = value; }
    public boolean isRequirePlatformAdministrator() { return requirePlatformAdministrator; }
    public void setRequirePlatformAdministrator(boolean value) { requirePlatformAdministrator = value; }
}
