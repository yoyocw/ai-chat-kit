package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;

import java.util.UUID;

/** 同一真实身份源提供同步身份、会话和权限 Port。 */
public final class PlatformInProcessAuthenticationAdapter
        implements AiInvocationContextPort, AiHostSessionPort, AiHostPermissionPort {
    private final PlatformLocalSessionSource source;

    public PlatformInProcessAuthenticationAdapter(PlatformLocalSessionSource source) { this.source = source; }

    @Override
    public AiInvocationContext capture(String expectedActorId) {
        AiInvocationContext context = captureCurrent();
        if (!context.getActorId().equals(expectedActorId)) {
            throw new AiIdentityException(AiIdentityError.FORBIDDEN);
        }
        return context;
    }

    @Override
    public AiInvocationContext captureCurrent() {
        AiHostSession session = source.captureCurrentSession();
        return new AiInvocationContext(session.getNamespace(), session.getTenantId(), session.getActorId(),
                UUID.randomUUID().toString());
    }

    @Override
    public AiHostSession currentSession(AiInvocationContext context) { return source.currentSession(context); }

    @Override
    public AiHostSession checkSession(AiInvocationContext context, String sessionId) {
        AiHostSession actual = source.currentSession(context);
        if (sessionId == null || !actual.getSessionId().equals(sessionId)) {
            throw new AiIdentityException(AiIdentityError.FORBIDDEN);
        }
        return actual;
    }

    @Override
    public boolean hasPermission(AiInvocationContext context, String permission) {
        return source.hasPermission(context, permission);
    }

    @Override
    public boolean isPlatformAdministrator(AiInvocationContext context) {
        return source.isPlatformAdministrator(context);
    }

    public void captureOrdinaryOrigin(String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            throw new AiIdentityException(AiIdentityError.CONFIGURATION);
        }
        source.captureCurrentSession();
    }
}
