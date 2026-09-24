package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/** v1 适配显式部署配置；默认不启用、不签发、不查询宿主。 */
@ConfigurationProperties(prefix = "ai-chat-kit.ai.mcp-jwt-v1")
public class AiMcpV1Properties {
    /** 是否装配普通应用授权；无工具应用不因此要求 JWT 签名材料。 */
    private boolean authorizationEnabled = false;
    /** 是否装配签发端。 */
    private boolean signingEnabled = false;
    /** 是否装配资源验签端。 */
    private boolean verificationEnabled = false;
    /** 固定可信签发者，不从请求解析选择。 */
    private String issuer;
    /** 固定资源服务受众。 */
    private String audience;
    /** 仅用于 iat 的时钟偏差秒数，0 到 30；不延长 exp。 */
    private int clockSkewSeconds = 10;
    /** 签发应用策略，启用签发时至少一条。 */
    private List<AiMcpV1AppPolicy> apps = new ArrayList<>();
    /** 资源接口策略，启用验签时至少一条。 */
    private List<AiMcpV1EndpointPolicy> endpoints = new ArrayList<>();

    /** @return 是否装配普通应用授权。 */
    public boolean getAuthorizationEnabled() { return authorizationEnabled; }
    /** @param value 是否装配普通应用授权。 */
    public void setAuthorizationEnabled(boolean value) { this.authorizationEnabled = value; }
    /** @return 是否装配签发端。 */
    public boolean getSigningEnabled() { return signingEnabled; }
    /** @param value 是否装配签发端。 */
    public void setSigningEnabled(boolean value) { this.signingEnabled = value; }
    /** @return 是否装配资源验签端。 */
    public boolean getVerificationEnabled() { return verificationEnabled; }
    /** @param value 是否装配资源验签端。 */
    public void setVerificationEnabled(boolean value) { this.verificationEnabled = value; }
    /** @return 固定可信签发者，不从请求解析选择。 */
    public String getIssuer() { return issuer; }
    /** @param value 固定可信签发者，不从请求解析选择。 */
    public void setIssuer(String value) { this.issuer = value; }
    /** @return 固定资源服务受众。 */
    public String getAudience() { return audience; }
    /** @param value 固定资源服务受众。 */
    public void setAudience(String value) { this.audience = value; }
    /** @return 仅用于 iat 的时钟偏差秒数，0 到 30；不延长 exp。 */
    public int getClockSkewSeconds() { return clockSkewSeconds; }
    /** @param value 仅用于 iat 的时钟偏差秒数，0 到 30；不延长 exp。 */
    public void setClockSkewSeconds(int value) { this.clockSkewSeconds = value; }
    /** @return 签发应用策略，启用签发时至少一条。 */
    public List<AiMcpV1AppPolicy> getApps() { return apps; }
    /** @param value 签发应用策略，启用签发时至少一条。 */
    public void setApps(List<AiMcpV1AppPolicy> value) { this.apps = value; }
    /** @return 资源接口策略，启用验签时至少一条。 */
    public List<AiMcpV1EndpointPolicy> getEndpoints() { return endpoints; }
    /** @param value 资源接口策略，启用验签时至少一条。 */
    public void setEndpoints(List<AiMcpV1EndpointPolicy> value) { this.endpoints = value; }
}
