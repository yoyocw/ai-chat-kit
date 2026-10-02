package io.github.yoyocw.aichatkit.mcp.jwt;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.Set;

/** legacy login-user-v1 的数值身份载荷；不含密钥，不代表已完成真实会话及数据权限核验。 */
public final class McpJwtV1Identity {
    /** 调用方数据库主键，仅作旧协议审计关联。 */
    private Long clientId;

    /** 调用方稳定业务编码，允许为空。 */
    private String clientCode;

    /** 调用方审计展示名称，允许为空。 */
    private String clientName;

    /** login-user-v1 的数值型真实用户编号，必须为正数；不接受不透明身份映射。 */
    private Long serviceUserId;

    /** 旧协议数值型租户编号，必须大于或等于0。 */
    private Long tenantId;

    /** 单次分页上限，允许1至200。 */
    private Integer maxPageSize;

    /** 旧协议用户昵称，仅用于认证上下文和审计。 */
    private String nickname;

    /** 旧协议部门编号，不构成可独立授权的数据权限快照。 */
    private Long deptId;

    /** JWT exp 对应的 UTC Unix 秒；验签返回值，签发期限由独立参数指定。 */
    @JsonProperty("exp")
    private Long expiresAtEpochSecond;

    /** 旧协议持久化会话编号，必须为正数；本值本身不是认证凭据。 */
    private Long accessTokenId;

    /** 委托入口允许的机器客户端编号，普通工具 JWT 可为空。 */
    private String delegationClientId;

    /** 委托入口所属业务系统，仍须由宿主复核当前绑定。 */
    private String delegationBusinessSystem;

    /** 委托入口部署环境，仍须由宿主核验。 */
    private String delegationEnvironment;

    /** 只为旧 DTO 验签映射兼容保留，不参与用户 JWT 签发或数据授权。 */
    private Set<Long> allowedCreatorIds = new HashSet<>();

    /** 非空只读端点集合；具体端点开放上限仍由资源服务校验。 */
    private Set<String> allowedEndpointCodes = new HashSet<>();

    /** @return 调用方数据库主键，仅作旧协议审计关联。 */
    public Long getClientId() { return clientId; }

    /** @param clientId 调用方数据库主键，仅作旧协议审计关联。 */
    public void setClientId(Long clientId) { this.clientId = clientId; }

    /** @return 调用方稳定业务编码，允许为空。 */
    public String getClientCode() { return clientCode; }

    /** @param clientCode 调用方稳定业务编码，允许为空。 */
    public void setClientCode(String clientCode) { this.clientCode = clientCode; }

    /** @return 调用方审计展示名称，允许为空。 */
    public String getClientName() { return clientName; }

    /** @param clientName 调用方审计展示名称，允许为空。 */
    public void setClientName(String clientName) { this.clientName = clientName; }

    /** @return login-user-v1 的数值型真实用户编号，必须为正数；不接受不透明身份映射。 */
    public Long getServiceUserId() { return serviceUserId; }

    /** @param serviceUserId login-user-v1 的数值型真实用户编号，必须为正数；不接受不透明身份映射。 */
    public void setServiceUserId(Long serviceUserId) { this.serviceUserId = serviceUserId; }

    /** @return 旧协议数值型租户编号，必须大于或等于0。 */
    public Long getTenantId() { return tenantId; }

    /** @param tenantId 旧协议数值型租户编号，必须大于或等于0。 */
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    /** @return 单次分页上限，允许1至200。 */
    public Integer getMaxPageSize() { return maxPageSize; }

    /** @param maxPageSize 单次分页上限，允许1至200。 */
    public void setMaxPageSize(Integer maxPageSize) { this.maxPageSize = maxPageSize; }

    /** @return 旧协议用户昵称，仅用于认证上下文和审计。 */
    public String getNickname() { return nickname; }

    /** @param nickname 旧协议用户昵称，仅用于认证上下文和审计。 */
    public void setNickname(String nickname) { this.nickname = nickname; }

    /** @return 旧协议部门编号，不构成可独立授权的数据权限快照。 */
    public Long getDeptId() { return deptId; }

    /** @param deptId 旧协议部门编号，不构成可独立授权的数据权限快照。 */
    public void setDeptId(Long deptId) { this.deptId = deptId; }

    /** @return JWT exp 对应的 UTC Unix 秒；验签返回值，签发期限由独立参数指定。 */
    public Long getExpiresAtEpochSecond() { return expiresAtEpochSecond; }

    /** @param expiresAtEpochSecond JWT exp 对应的 UTC Unix 秒；验签返回值，签发期限由独立参数指定。 */
    public void setExpiresAtEpochSecond(Long expiresAtEpochSecond) { this.expiresAtEpochSecond = expiresAtEpochSecond; }

    /** @return 旧协议持久化会话编号，必须为正数；本值本身不是认证凭据。 */
    public Long getAccessTokenId() { return accessTokenId; }

    /** @param accessTokenId 旧协议持久化会话编号，必须为正数；本值本身不是认证凭据。 */
    public void setAccessTokenId(Long accessTokenId) { this.accessTokenId = accessTokenId; }

    /** @return 委托入口允许的机器客户端编号，普通工具 JWT 可为空。 */
    public String getDelegationClientId() { return delegationClientId; }

    /** @param delegationClientId 委托入口允许的机器客户端编号，普通工具 JWT 可为空。 */
    public void setDelegationClientId(String delegationClientId) { this.delegationClientId = delegationClientId; }

    /** @return 委托入口所属业务系统，仍须由宿主复核当前绑定。 */
    public String getDelegationBusinessSystem() { return delegationBusinessSystem; }

    /** @param delegationBusinessSystem 委托入口所属业务系统，仍须由宿主复核当前绑定。 */
    public void setDelegationBusinessSystem(String delegationBusinessSystem) { this.delegationBusinessSystem = delegationBusinessSystem; }

    /** @return 委托入口部署环境，仍须由宿主核验。 */
    public String getDelegationEnvironment() { return delegationEnvironment; }

    /** @param delegationEnvironment 委托入口部署环境，仍须由宿主核验。 */
    public void setDelegationEnvironment(String delegationEnvironment) { this.delegationEnvironment = delegationEnvironment; }

    /** @return 只为旧 DTO 验签映射兼容保留，不参与用户 JWT 签发或数据授权。 */
    public Set<Long> getAllowedCreatorIds() { return allowedCreatorIds; }

    /** @param allowedCreatorIds 只为旧 DTO 验签映射兼容保留，不参与用户 JWT 签发或数据授权。 */
    public void setAllowedCreatorIds(Set<Long> allowedCreatorIds) { this.allowedCreatorIds = allowedCreatorIds; }

    /** @return 非空只读端点集合；具体端点开放上限仍由资源服务校验。 */
    public Set<String> getAllowedEndpointCodes() { return allowedEndpointCodes; }

    /** @param allowedEndpointCodes 非空只读端点集合；具体端点开放上限仍由资源服务校验。 */
    public void setAllowedEndpointCodes(Set<String> allowedEndpointCodes) { this.allowedEndpointCodes = allowedEndpointCodes; }

}
