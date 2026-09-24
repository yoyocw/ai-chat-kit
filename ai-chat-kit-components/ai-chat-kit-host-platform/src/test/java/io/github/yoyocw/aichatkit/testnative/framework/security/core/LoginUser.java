package io.github.yoyocw.aichatkit.testnative.framework.security.core;

import java.time.LocalDateTime;

/** Test-only native login snapshot. */
public final class LoginUser {
    private Long id;
    private Long tenantId;
    private Integer userType;
    private Long accessTokenId;
    private LocalDateTime expiresTime;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Integer getUserType() { return userType; }
    public void setUserType(Integer value) { userType = value; }
    public Long getAccessTokenId() { return accessTokenId; }
    public void setAccessTokenId(Long value) { accessTokenId = value; }
    public LocalDateTime getExpiresTime() { return expiresTime; }
    public void setExpiresTime(LocalDateTime value) { expiresTime = value; }
}
