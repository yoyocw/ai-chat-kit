package io.github.yoyocw.aichatkit.module.ai.contract.authorization;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 中立授权请求，不含原始登录令牌和宿主配置对象。 */
public final class AiInvocationAuthorizationRequest {
    /** 与本轮其他端口共用的身份及关联编号。 */
    private final AiInvocationContext context;
    /** 本轮实际调用应用。 */
    private final String appId;
    /** 可选 MCP 服务编号。 */
    private final String mcpId;
    /** 本轮工具范围，不含凭据。 */
    private final java.util.List<String> toolIds;
    /** 创建本轮快照；集合复制后不可变。 */
    public AiInvocationAuthorizationRequest(AiInvocationContext context, String appId, String mcpId, java.util.List<String> toolIds) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.appId = appId;
        this.mcpId = mcpId;
        this.toolIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(toolIds));
    }
    public String getNamespace() { return context.getNamespace(); }
    public String getTenantId() { return context.getTenantId(); }
    public String getActorId() { return context.getActorId(); }
    public String getInvocationId() { return context.getInvocationId(); }
    public String getAppId() { return appId; }
    public String getMcpId() { return mcpId; }
    public java.util.List<String> getToolIds() { return toolIds; }
}
