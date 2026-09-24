package io.github.yoyocw.aichatkit.module.ai.contract.identity;

/** 宿主真实会话源核验后的快照；仅含关联标识，不携带原始令牌，也不构成认证证明。 */
public final class AiHostSession {
    /** 宿主部署命名空间，用于隔离不同接入方。 */
    private final String namespace;
    /** 已核验会话所属租户的不透明标识。 */
    private final String tenantId;
    /** 已核验会话所属用户的不透明标识。 */
    private final String actorId;
    /** 可由宿主会话源复核的不透明会话标识，不得填入访问令牌。 */
    private final String sessionId;
    /** 会话失效时刻，UTC Unix 毫秒，必须为正数；使用方仍需检查是否已过期。 */
    private final long expiresAtMillis;

    /**
     * @param namespace 非空宿主命名空间
     * @param tenantId 非空已认证租户标识
     * @param actorId 非空已认证用户标识
     * @param sessionId 非空已核验会话标识，不是令牌
     * @param expiresAtMillis UTC Unix 毫秒失效时间
     * @throws IllegalArgumentException 必填标识为空或时间非正数
     */
    public AiHostSession(String namespace, String tenantId, String actorId, String sessionId, long expiresAtMillis) {
        this.namespace = requireIdentifier(namespace);
        this.tenantId = requireIdentifier(tenantId);
        this.actorId = requireIdentifier(actorId);
        this.sessionId = requireIdentifier(sessionId);
        if (expiresAtMillis <= 0) { throw new IllegalArgumentException("会话失效时间无效"); }
        this.expiresAtMillis = expiresAtMillis;
    }

    /** 校验结构，不把标识内容写入错误消息；有效性仍由宿主会话源核验。 */
    private static String requireIdentifier(String value) {
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException("会话标识不能为空"); }
        return value;
    }

    /** @return 宿主部署命名空间 */
    public String getNamespace() { return namespace; }
    /** @return 已认证租户标识 */
    public String getTenantId() { return tenantId; }
    /** @return 已认证用户标识 */
    public String getActorId() { return actorId; }
    /** @return 非令牌的会话关联标识 */
    public String getSessionId() { return sessionId; }
    /** @return UTC Unix 毫秒失效时刻 */
    public long getExpiresAtMillis() { return expiresAtMillis; }
}
