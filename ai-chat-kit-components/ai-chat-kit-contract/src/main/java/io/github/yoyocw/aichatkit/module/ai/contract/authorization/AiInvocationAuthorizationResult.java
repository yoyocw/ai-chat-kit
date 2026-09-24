package io.github.yoyocw.aichatkit.module.ai.contract.authorization;

/** 仅当前调用使用的成功授权结果；禁止持久化或输出，拒绝通过异常表达。 */
public final class AiInvocationAuthorizationResult {
    /** 可选工具委托头，不是原始用户登录令牌；不提供 Bean getter。 */
    private final transient String toolAuthorization;
    /** @param toolAuthorization 无工具时为 null，有工具时为非空委托凭据 */
    public AiInvocationAuthorizationResult(String toolAuthorization) {
        this.toolAuthorization = toolAuthorization;
    }
    /** @return 是否包含本轮工具凭据 */
    public boolean hasToolCredential() { return toolAuthorization != null; }
    /** 仅交给下游工具传输适配，禁止用于日志或响应序列化。 */
    public String toolAuthorization() { return toolAuthorization; }
    @Override
    public String toString() { return "AiInvocationAuthorizationResult[authorized]"; }
}
