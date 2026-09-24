package io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.dto;

import java.time.LocalDateTime;

/** Test-only native DTO with the actual identity fields read by the bridge. */
public final class OAuth2AccessTokenCheckRespDTO {
    private Long accessTokenId;
    private Long userId;
    private Long tenantId;
    private Integer userType;
    private LocalDateTime expiresTime;

    public Long getAccessTokenId() { return accessTokenId; }
    public void setAccessTokenId(Long value) { accessTokenId = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Integer getUserType() { return userType; }
    public void setUserType(Integer value) { userType = value; }
    public LocalDateTime getExpiresTime() { return expiresTime; }
    public void setExpiresTime(LocalDateTime value) { expiresTime = value; }
}
