package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.config;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopReceiver;

/** 部署级接收方配置；绑定后由协调器验证并复制，不能由请求覆盖。 */
public class AiStopReceiverProperties {
    /** 部署内唯一停止接收者。 */
    private String audience;
    /** 原调用方、消费者及用户共同所属真实租户，不透明标识。 */
    private String tenantId;
    /** 原业务系统绑定。 */
    private String businessSystem;
    /** 原业务环境绑定。 */
    private String environment;
    /** 真实原机器客户端编码。 */
    private String originalClientId;
    /** 原客户端真实实体标识，不透明。 */
    private String originalClientRecordId;
    /** 独立消费客户端编码，与原机器不同。 */
    private String consumerClientId;
    /** 独立消费客户端真实实体标识。 */
    private String consumerClientRecordId;
    /** 原机器必须获准的资源，由部署固定。 */
    private String originalResourceId;
    /** 消费者必须获准的资源，由部署固定。 */
    private String consumerResourceId;
    /** 原机器必须获准的 scope，由部署固定。 */
    private String originalScope;
    /** 独立消费 scope，由部署固定。 */
    private String consumerScope;
    /** @return 部署内唯一停止接收者。 */
    public String getAudience() { return audience; }
    /** @param value 部署内唯一停止接收者。 */
    public void setAudience(String value) { audience = value; }
    /** @return 原调用方、消费者及用户共同所属真实租户，不透明标识。 */
    public String getTenantId() { return tenantId; }
    /** @param value 原调用方、消费者及用户共同所属真实租户，不透明标识。 */
    public void setTenantId(String value) { tenantId = value; }
    /** @return 原业务系统绑定。 */
    public String getBusinessSystem() { return businessSystem; }
    /** @param value 原业务系统绑定。 */
    public void setBusinessSystem(String value) { businessSystem = value; }
    /** @return 原业务环境绑定。 */
    public String getEnvironment() { return environment; }
    /** @param value 原业务环境绑定。 */
    public void setEnvironment(String value) { environment = value; }
    /** @return 真实原机器客户端编码。 */
    public String getOriginalClientId() { return originalClientId; }
    /** @param value 真实原机器客户端编码。 */
    public void setOriginalClientId(String value) { originalClientId = value; }
    /** @return 原客户端真实实体标识，不透明。 */
    public String getOriginalClientRecordId() { return originalClientRecordId; }
    /** @param value 原客户端真实实体标识，不透明。 */
    public void setOriginalClientRecordId(String value) { originalClientRecordId = value; }
    /** @return 独立消费客户端编码，与原机器不同。 */
    public String getConsumerClientId() { return consumerClientId; }
    /** @param value 独立消费客户端编码，与原机器不同。 */
    public void setConsumerClientId(String value) { consumerClientId = value; }
    /** @return 独立消费客户端真实实体标识。 */
    public String getConsumerClientRecordId() { return consumerClientRecordId; }
    /** @param value 独立消费客户端真实实体标识。 */
    public void setConsumerClientRecordId(String value) { consumerClientRecordId = value; }
    /** @return 原机器必须获准的资源，由部署固定。 */
    public String getOriginalResourceId() { return originalResourceId; }
    /** @param value 原机器必须获准的资源，由部署固定。 */
    public void setOriginalResourceId(String value) { originalResourceId = value; }
    /** @return 消费者必须获准的资源，由部署固定。 */
    public String getConsumerResourceId() { return consumerResourceId; }
    /** @param value 消费者必须获准的资源，由部署固定。 */
    public void setConsumerResourceId(String value) { consumerResourceId = value; }
    /** @return 原机器必须获准的 scope，由部署固定。 */
    public String getOriginalScope() { return originalScope; }
    /** @param value 原机器必须获准的 scope，由部署固定。 */
    public void setOriginalScope(String value) { originalScope = value; }
    /** @return 独立消费 scope，由部署固定。 */
    public String getConsumerScope() { return consumerScope; }
    /** @param value 独立消费 scope，由部署固定。 */
    public void setConsumerScope(String value) { consumerScope = value; }
    /** @param namespace 固定宿主命名空间 @return 不可变部署快照，仍由协调器严格校验 */
    public AiStopReceiver snapshot(String namespace) {
        return new AiStopReceiver(namespace, audience, tenantId, businessSystem, environment, originalClientId, originalClientRecordId, consumerClientId, consumerClientRecordId, originalResourceId, consumerResourceId, originalScope, consumerScope);
    }
}
