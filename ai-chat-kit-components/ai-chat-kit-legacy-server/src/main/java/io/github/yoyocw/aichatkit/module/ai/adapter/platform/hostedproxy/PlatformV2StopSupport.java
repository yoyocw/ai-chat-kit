package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;

/** 仅真实林业宿主边界使用的数值映射与安全错误；通用协调器不得依赖此类。 */
public final class PlatformV2StopSupport {
    private PlatformV2StopSupport() { }

    /** @param value 林业真实源产生的规范数字标识 @param allowZero 租户可为零 @return 原真实数值 */
    public static Long number(String value, boolean allowZero) {
        try {
            long result = Long.parseLong(value);
            if (result < (allowZero ? 0 : 1) || !Long.toString(result).equals(value)) { throw failure(); }
            return result;
        } catch (RuntimeException exception) { throw failure(); }
    }

    /** @param authorization 宿主真实普通Bearer凭据 @return 裸普通凭据，明确拒绝MCP工具凭据 */
    public static String raw(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 8192) {
            throw failure();
        }
        String token = authorization.substring(7);
        if (token.isEmpty() || token.startsWith("mcp_") || token.startsWith("ai_hosted_stop_")
                || !token.equals(token.trim())) { throw failure(); }
        return token;
    }

    /** @param user 已真实源核验的用户 @param caller 当前已核验机器来源 @return 旧林业数值来源的严格同值映射 */
    public static AiCallerOrigin numericOrigin(AiHostSession user, AiCallerOrigin caller) {
        if (user == null || caller == null || !"platform".equals(user.getNamespace())
                || user.getExpiresAtMillis() <= System.currentTimeMillis()
                || !user.getTenantId().equals(caller.getTenantIdentifier())
                || !user.getActorId().equals(caller.getActorIdentifier())) { throw failure(); }
        return new AiCallerOrigin(number(user.getTenantId(), true), number(user.getActorId(), false),
                number(caller.getClientRecordIdentifier(), false), caller.getClientId(),
                caller.getBusinessSystem(), caller.getEnvironment());
    }

    /** @param context 作用域实际捕获身份 @param user 原真实用户快照，不能拿请求参数替代 */
    public static void context(AiInvocationContext context, AiHostSession user) {
        if (context == null || !user.getNamespace().equals(context.getNamespace())
                || !user.getTenantId().equals(context.getTenantId()) || !user.getActorId().equals(context.getActorId())
                || context.getInvocationId() == null || context.getInvocationId().trim().isEmpty()) { throw failure(); }
    }

    /** @return 无凭据、元数据或底层cause的固定错误 */
    public static IllegalStateException failure() { return new IllegalStateException("林业 v2 停止授权不可用"); }
}
