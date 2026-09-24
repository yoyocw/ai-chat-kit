package io.github.yoyocw.aichatkit.module.ai.contract.identity;

/** 同步宿主入口确认的身份快照；标识不是认证证明，适配器仍需核对可信上下文。 */
public final class AiInvocationContext {
    /** 同轮调用关联编号，不提供幂等保证。 */
    private final String invocationId;
    /** 部署固定命名空间，当前不代表已支持跨命名空间存储。 */
    private final String namespace;
    /** 已认证租户的不透明标识。 */
    private final String tenantId;
    /** 已认证用户的不透明标识。 */
    private final String actorId;

    /** @param namespace 宿主命名空间
     * @param tenantId 已认证租户
     * @param actorId 已认证用户
     * @param invocationId 同步入口捕获的本轮编号 */
    public AiInvocationContext(String namespace, String tenantId, String actorId, String invocationId) {
        this.invocationId = invocationId;
        this.namespace = namespace;
        this.tenantId = tenantId;
        this.actorId = actorId;
    }
    public String getInvocationId() { return invocationId; }
    public String getNamespace() { return namespace; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }
}
