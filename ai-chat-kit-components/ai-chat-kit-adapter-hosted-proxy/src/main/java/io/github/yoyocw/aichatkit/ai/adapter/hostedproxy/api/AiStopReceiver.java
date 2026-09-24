package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 停止接收方不可变部署快照，票据完整绑定所有字段。 */
public final class AiStopReceiver {
    /** 按完整配置比较，任何接收实体、scope或resource变化都会使旧票据失效。 */
    @Override
    public boolean equals(Object value) {
        if (!(value instanceof AiStopReceiver)) { return false; }
        AiStopReceiver other = (AiStopReceiver) value;
        return java.util.Objects.equals(namespace, other.namespace)
                && java.util.Objects.equals(audience, other.audience)
                && java.util.Objects.equals(tenantId, other.tenantId)
                && java.util.Objects.equals(businessSystem, other.businessSystem)
                && java.util.Objects.equals(environment, other.environment)
                && java.util.Objects.equals(originalClientId, other.originalClientId)
                && java.util.Objects.equals(originalClientRecordId, other.originalClientRecordId)
                && java.util.Objects.equals(consumerClientId, other.consumerClientId)
                && java.util.Objects.equals(consumerClientRecordId, other.consumerClientRecordId)
                && java.util.Objects.equals(originalResourceId, other.originalResourceId)
                && java.util.Objects.equals(consumerResourceId, other.consumerResourceId)
                && java.util.Objects.equals(originalScope, other.originalScope)
                && java.util.Objects.equals(consumerScope, other.consumerScope);
    }
    /** @return 与完整配置相等规则一致的散列 */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(namespace, audience, tenantId, businessSystem, environment, originalClientId, originalClientRecordId, consumerClientId, consumerClientRecordId, originalResourceId, consumerResourceId, originalScope, consumerScope);
    }
    /** 固定宿主命名空间。 */
    private final String namespace;
    /** 部署内唯一停止接收者。 */
    private final String audience;
    /** 原调用方、消费者及用户共同所属真实租户，不透明标识。 */
    private final String tenantId;
    /** 原业务系统绑定。 */
    private final String businessSystem;
    /** 原业务环境绑定。 */
    private final String environment;
    /** 真实原机器客户端编码。 */
    private final String originalClientId;
    /** 原客户端真实实体标识，不透明。 */
    private final String originalClientRecordId;
    /** 独立消费客户端编码，与原机器不同。 */
    private final String consumerClientId;
    /** 独立消费客户端真实实体标识。 */
    private final String consumerClientRecordId;
    /** 原机器必须获准的资源，由部署固定。 */
    private final String originalResourceId;
    /** 消费者必须获准的资源，由部署固定。 */
    private final String consumerResourceId;
    /** 原机器必须获准的 scope，由部署固定。 */
    private final String originalScope;
    /** 独立消费 scope，由部署固定。 */
    private final String consumerScope;

    /** 创建不可变快照；实际身份及有效性由宿主源和协调层复核。 */
    @JsonCreator
    public AiStopReceiver(
            @JsonProperty("namespace") String namespace,
            @JsonProperty("audience") String audience,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("businessSystem") String businessSystem,
            @JsonProperty("environment") String environment,
            @JsonProperty("originalClientId") String originalClientId,
            @JsonProperty("originalClientRecordId") String originalClientRecordId,
            @JsonProperty("consumerClientId") String consumerClientId,
            @JsonProperty("consumerClientRecordId") String consumerClientRecordId,
            @JsonProperty("originalResourceId") String originalResourceId,
            @JsonProperty("consumerResourceId") String consumerResourceId,
            @JsonProperty("originalScope") String originalScope,
            @JsonProperty("consumerScope") String consumerScope) {
        this.namespace = namespace;
        this.audience = audience;
        this.tenantId = tenantId;
        this.businessSystem = businessSystem;
        this.environment = environment;
        this.originalClientId = originalClientId;
        this.originalClientRecordId = originalClientRecordId;
        this.consumerClientId = consumerClientId;
        this.consumerClientRecordId = consumerClientRecordId;
        this.originalResourceId = originalResourceId;
        this.consumerResourceId = consumerResourceId;
        this.originalScope = originalScope;
        this.consumerScope = consumerScope;
    }

    /** @return 固定宿主命名空间。 */
    public String getNamespace() { return namespace; }
    /** @return 部署内唯一停止接收者。 */
    public String getAudience() { return audience; }
    /** @return 原调用方、消费者及用户共同所属真实租户，不透明标识。 */
    public String getTenantId() { return tenantId; }
    /** @return 原业务系统绑定。 */
    public String getBusinessSystem() { return businessSystem; }
    /** @return 原业务环境绑定。 */
    public String getEnvironment() { return environment; }
    /** @return 真实原机器客户端编码。 */
    public String getOriginalClientId() { return originalClientId; }
    /** @return 原客户端真实实体标识，不透明。 */
    public String getOriginalClientRecordId() { return originalClientRecordId; }
    /** @return 独立消费客户端编码，与原机器不同。 */
    public String getConsumerClientId() { return consumerClientId; }
    /** @return 独立消费客户端真实实体标识。 */
    public String getConsumerClientRecordId() { return consumerClientRecordId; }
    /** @return 原机器必须获准的资源，由部署固定。 */
    public String getOriginalResourceId() { return originalResourceId; }
    /** @return 消费者必须获准的资源，由部署固定。 */
    public String getConsumerResourceId() { return consumerResourceId; }
    /** @return 原机器必须获准的 scope，由部署固定。 */
    public String getOriginalScope() { return originalScope; }
    /** @return 独立消费 scope，由部署固定。 */
    public String getConsumerScope() { return consumerScope; }
}
