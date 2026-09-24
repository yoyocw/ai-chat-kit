package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service;

import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.Objects;

/** 模块内部的严格关联校验；不把参数转换成身份，也不回显底层敏感异常。 */
final class AiMcpV1Checks {
    private AiMcpV1Checks() { }

    /** 检查部署值及标识，不通过 trim 改写其实际值。 */
    static String text(String value) {
        if (value == null || value.trim().isEmpty()) { throw denied(); }
        return value;
    }

    /** 检查真实快照有效期，秒级签发不能使用不足一秒的剩余会话。 */
    static long expiry(AiHostSession session) {
        if (session == null || session.getExpiresAtMillis() / 1000 <= System.currentTimeMillis() / 1000) {
            throw denied();
        }
        return session.getExpiresAtMillis() / 1000;
    }

    /** 验证身份上下文与已核验会话严格一致。 */
    static void matches(String namespace, AiInvocationContext context, AiHostSession session) {
        expiry(session);
        if (context == null || !namespace.equals(text(context.getNamespace()))
                || !namespace.equals(session.getNamespace())
                || !text(context.getTenantId()).equals(session.getTenantId())
                || !text(context.getActorId()).equals(session.getActorId())) { throw denied(); }
        text(context.getInvocationId());
    }

    /** 不允许另一个同用户有效会话替代 JWT 原会话。 */
    static void sameSession(AiHostSession expected, AiHostSession actual) {
        expiry(expected);
        expiry(actual);
        if (!expected.getNamespace().equals(actual.getNamespace())
                || !expected.getTenantId().equals(actual.getTenantId())
                || !expected.getActorId().equals(actual.getActorId())
                || !expected.getSessionId().equals(actual.getSessionId())) { throw denied(); }
    }

    /** 仅复制数值映射三元组；忽略 SPI 附带的权限及委托字段，防止扩大授权。 */
    static McpJwtV1Identity subject(McpJwtV1Identity source) {
        if (source == null) { throw denied(); }
        Long user = source.getServiceUserId();
        Long session = source.getAccessTokenId();
        Long tenant = source.getTenantId();
        if (user == null || user <= 0 || session == null || session <= 0 || tenant == null || tenant < 0) {
            throw denied();
        }
        McpJwtV1Identity copy = new McpJwtV1Identity();
        copy.setServiceUserId(user);
        copy.setAccessTokenId(session);
        copy.setTenantId(tenant);
        return copy;
    }

    /** 验签后的数值三元组必须经真实源映射回同一组，不能只核验会话有效。 */
    static void sameSubject(McpJwtV1Identity expected, McpJwtV1Identity actual) {
        McpJwtV1Identity checked = subject(actual);
        if (!Objects.equals(expected.getServiceUserId(), checked.getServiceUserId())
                || !Objects.equals(expected.getAccessTokenId(), checked.getAccessTokenId())
                || !Objects.equals(expected.getTenantId(), checked.getTenantId())) { throw denied(); }
    }

    /** 返回固定安全错误，无原始凭据、身份、配置值及异常链。 */
    static IllegalStateException denied() { return new IllegalStateException("MCP 宿主授权校验失败"); }
}
