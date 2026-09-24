package io.github.yoyocw.aichatkit.module.ai.contract.authorization;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 宿主原生权限判定，不预设 system 角色、固定租户或默认放行规则。 */
public interface AiHostPermissionPort {
    /**
     * @param context 已认证且归属已核对的本轮上下文
     * @param permission 待检查的非空权限编码，由接入端约定
     * @return 真实权限源明确允许时为 true，明确拒绝时为 false
     * @throws IllegalStateException 身份失效或权限源不可用；不得将检查异常转换成其他身份重试
     */
    boolean hasPermission(AiInvocationContext context, String permission);

    /**
     * @param context 已认证且归属已核对的本轮上下文
     * @return 宿主真实权限源确认平台管理身份时为 true
     * @throws IllegalStateException 身份失效或权限源不可用；不得从空租户或固定角色编号推断管理员
     */
    boolean isPlatformAdministrator(AiInvocationContext context);
}
