package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

/** 宿主源认证的真实机器会话及当前授权绑定，不是原始凭据。 */
public final class AiStopMachineIdentity {
    /** 真实宿主命名空间。 */
    private final String namespace;
    /** 真实租户标识。 */
    private final String tenantId;
    /** 原访问会话的不透明关联标识，不是Token。 */
    private final String sessionId;
    /** 当前真实客户端实体标识。 */
    private final String clientRecordId;
    /** 实际客户端编码。 */
    private final String clientId;
    /** 原机器当前绑定的系统；消费者可为空。 */
    private final String businessSystem;
    /** 原机器当前绑定的环境；消费者可为空。 */
    private final String environment;
    /** 原机器当前授权应用；消费者为空集合。 */
    private final Set<String> allowedAppIds;
    /** 真实会话失效时刻，UTC Unix毫秒。 */
    private final long expiresAtMillis;

    /** 创建不可变快照；实际身份及有效性由宿主源和协调层复核。 */
    @JsonCreator
    public AiStopMachineIdentity(
            @JsonProperty("namespace") String namespace,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("clientRecordId") String clientRecordId,
            @JsonProperty("clientId") String clientId,
            @JsonProperty("businessSystem") String businessSystem,
            @JsonProperty("environment") String environment,
            @JsonProperty("allowedAppIds") Set<String> allowedAppIds,
            @JsonProperty("expiresAtMillis") long expiresAtMillis) {
        this.namespace = namespace;
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.clientRecordId = clientRecordId;
        this.clientId = clientId;
        this.businessSystem = businessSystem;
        this.environment = environment;
        this.allowedAppIds = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Objects.requireNonNull(allowedAppIds)));
        this.expiresAtMillis = expiresAtMillis;
    }

    /** @return 真实宿主命名空间。 */
    public String getNamespace() { return namespace; }
    /** @return 真实租户标识。 */
    public String getTenantId() { return tenantId; }
    /** @return 原访问会话的不透明关联标识，不是Token。 */
    public String getSessionId() { return sessionId; }
    /** @return 当前真实客户端实体标识。 */
    public String getClientRecordId() { return clientRecordId; }
    /** @return 实际客户端编码。 */
    public String getClientId() { return clientId; }
    /** @return 原机器当前绑定的系统；消费者可为空。 */
    public String getBusinessSystem() { return businessSystem; }
    /** @return 原机器当前绑定的环境；消费者可为空。 */
    public String getEnvironment() { return environment; }
    /** @return 原机器当前授权应用；消费者为空集合。 */
    public Set<String> getAllowedAppIds() { return allowedAppIds; }
    /** @return 真实会话失效时刻，UTC Unix毫秒。 */
    public long getExpiresAtMillis() { return expiresAtMillis; }
}
