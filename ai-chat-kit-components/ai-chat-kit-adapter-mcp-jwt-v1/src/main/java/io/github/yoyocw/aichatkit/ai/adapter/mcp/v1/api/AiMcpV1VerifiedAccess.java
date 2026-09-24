package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;

/** 已验签并复核的用户访问范围；资源宿主仍须实施租户、数据行权限和资源归属过滤。 */
public final class AiMcpV1VerifiedAccess {
    /** 原真实宿主会话，期限不超过 JWT；不包含访问令牌。 */
    private final AiHostSession session;
    /** 本次服务端选定且已授权的接口码。 */
    private final String endpointCode;
    /** JWT 与资源端策略取较小值后的页大小上限，单位条。 */
    private final int maxPageSize;

    /** @param session 已核验会话 @param endpointCode 已授权接口 @param maxPageSize 条数上限 */
    public AiMcpV1VerifiedAccess(AiHostSession session, String endpointCode, int maxPageSize) {
        this.session = session;
        this.endpointCode = endpointCode;
        this.maxPageSize = maxPageSize;
    }
    /** @return 需由资源宿主绑定并进行数据权限过滤的真实会话 */
    public AiHostSession getSession() { return session; }
    /** @return 本次接口码，不授权其他接口 */
    public String getEndpointCode() { return endpointCode; }
    /** @return 最大分页条数 */
    public int getMaxPageSize() { return maxPageSize; }
}
