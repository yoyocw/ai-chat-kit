package io.github.yoyocw.aichatkit.module.ai.contract.authorization;

/** 每轮应用与工具授权端口；拒绝时抛出，不能降级为无凭据成功。 */
public interface AiInvocationAuthorizationPort {
    /** @param request 可信同步入口生成的本轮请求；标识本身不是身份证明
     * @return 已验证结果，可在无工具时不携带凭据
     * @throws IllegalStateException 身份、应用、工具范围无效或认证服务失败 */
    AiInvocationAuthorizationResult authorize(AiInvocationAuthorizationRequest request);
}
