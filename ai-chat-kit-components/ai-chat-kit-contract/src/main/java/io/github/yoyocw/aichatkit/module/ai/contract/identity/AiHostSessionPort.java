package io.github.yoyocw.aichatkit.module.ai.contract.identity;

/** 宿主会话核验能力，可由本地认证框架或远程会话源实现，不绑定 HTTP 或 system 模块。 */
public interface AiHostSessionPort {
    /**
     * 从当前已认证同步上下文捕获会话，并核对租户和用户与本轮身份一致。
     * @param context 同步入口捕获的可信本轮上下文
     * @return 非空、未失效且身份匹配的真实会话快照
     * @throws IllegalStateException 未登录、身份不符、过期或会话源不可用；不得返回假会话
     */
    AiHostSession currentSession(AiInvocationContext context);

    /**
     * 对已验签委托携带的会话关联执行真实源复核；关联标识本身不提供认证授权。
     * @param context 已校验签名、受众及用途后建立的可信本轮上下文
     * @param sessionId 已验签关联的会话标识，不是原始访问令牌
     * @return 非空、未失效且 namespace/tenant/actor/session 均匹配的会话快照
     * @throws IllegalStateException 会话失效、归属不符或会话源不可用；失败必须拒绝执行
     */
    AiHostSession checkSession(AiInvocationContext context, String sessionId);
}
