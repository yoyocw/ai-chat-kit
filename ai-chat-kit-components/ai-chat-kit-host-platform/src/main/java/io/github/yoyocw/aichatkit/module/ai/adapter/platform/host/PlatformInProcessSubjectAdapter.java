package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 原生数值用户、租户和访问令牌记录的双向映射。 */
public final class PlatformInProcessSubjectAdapter implements AiMcpV1SubjectPort {
    private final PlatformLocalSessionSource source;

    public PlatformInProcessSubjectAdapter(PlatformLocalSessionSource source) {
        if (source == null) { throw denied(); }
        this.source = source;
    }

    @Override
    public McpJwtV1Identity mapSession(AiInvocationContext context, AiHostSession session) {
        if (context == null || session == null || !source.namespace().equals(context.getNamespace())
                || !source.namespace().equals(session.getNamespace())
                || context.getInvocationId() == null || context.getInvocationId().trim().isEmpty()) {
            throw denied();
        }
        long userId = canonical(context.getActorId(), false);
        long tenantId = canonical(context.getTenantId(), true);
        long accessTokenId = canonical(session.getSessionId(), false);
        if (!Long.toString(userId).equals(session.getActorId())
                || !Long.toString(tenantId).equals(session.getTenantId())) { throw denied(); }
        try {
            AiHostSession current = source.currentSession(context);
            same(session, current);
            McpJwtV1Identity identity = new McpJwtV1Identity();
            identity.setServiceUserId(userId);
            identity.setTenantId(tenantId);
            identity.setAccessTokenId(accessTokenId);
            return identity;
        } catch (RuntimeException ex) {
            throw denied();
        }
    }

    @Override
    public AiHostSession resolveSession(McpJwtV1Identity verifiedProof) {
        if (verifiedProof == null || verifiedProof.getServiceUserId() == null
                || verifiedProof.getServiceUserId() <= 0 || verifiedProof.getTenantId() == null
                || verifiedProof.getTenantId() < 0 || verifiedProof.getAccessTokenId() == null
                || verifiedProof.getAccessTokenId() <= 0) { throw denied(); }
        try {
            AiHostSession resolved = source.resolveSession(verifiedProof.getServiceUserId(),
                    verifiedProof.getTenantId(), verifiedProof.getAccessTokenId());
            if (resolved == null || !source.namespace().equals(resolved.getNamespace())
                    || !Long.toString(verifiedProof.getServiceUserId()).equals(resolved.getActorId())
                    || !Long.toString(verifiedProof.getTenantId()).equals(resolved.getTenantId())
                    || !Long.toString(verifiedProof.getAccessTokenId()).equals(resolved.getSessionId())
                    || resolved.getExpiresAtMillis() <= System.currentTimeMillis()) { throw denied(); }
            return resolved;
        } catch (RuntimeException ex) {
            throw denied();
        }
    }

    private static void same(AiHostSession expected, AiHostSession actual) {
        if (actual == null || !expected.getNamespace().equals(actual.getNamespace())
                || !expected.getTenantId().equals(actual.getTenantId())
                || !expected.getActorId().equals(actual.getActorId())
                || !expected.getSessionId().equals(actual.getSessionId())
                || expected.getExpiresAtMillis() <= System.currentTimeMillis()
                || actual.getExpiresAtMillis() <= System.currentTimeMillis()) { throw denied(); }
    }

    private static long canonical(String text, boolean allowZero) {
        try {
            long value = Long.parseLong(text);
            if ((allowZero ? value < 0 : value <= 0) || !Long.toString(value).equals(text)) {
                throw denied();
            }
            return value;
        } catch (RuntimeException ex) {
            throw denied();
        }
    }

    private static AiIdentityException denied() {
        return new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
    }
}
