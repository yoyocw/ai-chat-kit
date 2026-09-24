package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.adapter.platform.host.PlatformLocalBeanResolver.NativeIdentity;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiDelegatedCallContext;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.Objects;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 同进程模式中唯一访问宿主原生身份与权限服务的边界。 */
public final class PlatformLocalSessionSource {
    private final PlatformLocalBeanResolver beans;
    private final Long platformTenantId;
    private final String namespace;

    public PlatformLocalSessionSource(PlatformLocalBeanResolver beans, Long platformTenantId, String namespace) {
        if (beans == null || (platformTenantId != null && platformTenantId < 0)
                || namespace == null || namespace.trim().isEmpty() || !namespace.equals(namespace.trim())) {
            throw failure(AiIdentityError.CONFIGURATION);
        }
        this.beans = beans;
        this.platformTenantId = platformTenantId;
        this.namespace = namespace;
    }

    public String namespace() { return namespace; }

    /** 当前普通 Bearer、LoginUser 和数据库访问令牌记录必须属于同一身份。 */
    public AiHostSession captureCurrentSession() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes instanceof ServletRequestAttributes
                    ? ((ServletRequestAttributes) attributes).getRequest() : null;
            if (request == null) { throw failure(AiIdentityError.UNAUTHENTICATED); }
            if (request.getAttribute(AiDelegatedCallContext.class.getName()) != null) {
                throw failure(AiIdentityError.FORBIDDEN);
            }
            NativeIdentity login = beans.currentLogin();
            validateLogin(login);
            NativeIdentity token = beans.checkAccessToken(bearer(request));
            if (token.getAccessTokenId() == null || token.getAccessTokenId() <= 0) {
                throw failure(AiIdentityError.VERIFICATION_FAILED);
            }
            NativeIdentity record = beans.checkAccessTokenSession(token.getAccessTokenId());
            validateRecord(token);
            validateRecord(record);
            if (!same(token, record) || !Objects.equals(login.getAccessTokenId(), record.getAccessTokenId())
                    || !Objects.equals(login.getUserId(), record.getUserId())
                    || !Objects.equals(login.getTenantId(), record.getTenantId())
                    || !Objects.equals(login.getUserType(), record.getUserType())) {
                throw failure(AiIdentityError.FORBIDDEN);
            }
            long expiresAt = Math.min(expiry(token.getExpiresTime()),
                    Math.min(expiry(record.getExpiresTime()), expiry(login.getExpiresTime())));
            if (expiresAt <= System.currentTimeMillis()) { throw failure(AiIdentityError.UNAUTHENTICATED); }
            return new AiHostSession(namespace, record.getTenantId().toString(), record.getUserId().toString(),
                    record.getAccessTokenId().toString(), expiresAt);
        } catch (AiIdentityException ex) {
            throw failure(ex.getError());
        } catch (RuntimeException ex) {
            // 原服务异常及响应正文不得传播到调用端。
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    public AiHostSession currentSession(AiInvocationContext context) {
        AiHostSession actual = captureCurrentSession();
        match(context, actual);
        return actual;
    }

    /** 只供已验签证明绑定复核；按 ID 查询绝不建立普通登录。 */
    public AiHostSession resolveSession(long userId, long tenantId, long accessTokenId) {
        if (userId <= 0 || tenantId < 0 || accessTokenId <= 0) {
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
        try {
            NativeIdentity record = beans.checkAccessTokenSession(accessTokenId);
            validateRecord(record);
            if (record.getUserId() != userId || record.getTenantId() != tenantId
                    || record.getAccessTokenId() != accessTokenId) { throw failure(AiIdentityError.FORBIDDEN); }
            long expiresAt = expiry(record.getExpiresTime());
            if (expiresAt <= System.currentTimeMillis()) { throw failure(AiIdentityError.UNAUTHENTICATED); }
            return new AiHostSession(namespace, Long.toString(tenantId), Long.toString(userId),
                    Long.toString(accessTokenId), expiresAt);
        } catch (AiIdentityException ex) {
            throw failure(ex.getError());
        } catch (RuntimeException ex) {
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    public boolean hasPermission(AiInvocationContext context, String permission) {
        AiHostSession session = currentSession(context);
        String nativePermission = PlatformHostPermissions.resolve(permission);
        try {
            return beans.hasAnyPermissions(Long.parseLong(session.getActorId()), nativePermission);
        } catch (AiIdentityException ex) {
            throw failure(ex.getError());
        } catch (RuntimeException ex) {
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    public boolean isPlatformAdministrator(AiInvocationContext context) {
        AiHostSession session = currentSession(context);
        if (platformTenantId == null || !platformTenantId.toString().equals(session.getTenantId())) {
            return false;
        }
        try {
            return beans.hasAnyRoles(Long.parseLong(session.getActorId()), "super_admin");
        } catch (AiIdentityException ex) {
            throw failure(ex.getError());
        } catch (RuntimeException ex) {
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    private void validateLogin(NativeIdentity login) {
        if (login == null) { throw failure(AiIdentityError.UNAUTHENTICATED); }
        if (login.getUserId() == null || login.getUserId() <= 0
                || login.getTenantId() == null || login.getTenantId() < 0
                || login.getAccessTokenId() == null || login.getAccessTokenId() <= 0
                || login.getExpiresTime() == null) { throw failure(AiIdentityError.VERIFICATION_FAILED); }
        if (!beans.adminUserType().equals(login.getUserType())
                || !Objects.equals(login.getTenantId(), beans.currentTenantId())
                || beans.tenantIgnored() || beans.skipPermissionCheck()) {
            throw failure(AiIdentityError.FORBIDDEN);
        }
        if (expiry(login.getExpiresTime()) <= System.currentTimeMillis()) {
            throw failure(AiIdentityError.UNAUTHENTICATED);
        }
    }

    private void validateRecord(NativeIdentity value) {
        if (value.getAccessTokenId() == null || value.getAccessTokenId() <= 0
                || value.getUserId() == null || value.getUserId() <= 0
                || value.getTenantId() == null || value.getTenantId() < 0
                || value.getExpiresTime() == null
                || !beans.adminUserType().equals(value.getUserType())) {
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    private static boolean same(NativeIdentity token, NativeIdentity record) {
        return Objects.equals(token.getAccessTokenId(), record.getAccessTokenId())
                && Objects.equals(token.getUserId(), record.getUserId())
                && Objects.equals(token.getTenantId(), record.getTenantId())
                && Objects.equals(token.getUserType(), record.getUserType());
    }

    private static void match(AiInvocationContext context, AiHostSession actual) {
        if (context == null || context.getInvocationId() == null || context.getInvocationId().trim().isEmpty()) {
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
        if (!actual.getNamespace().equals(context.getNamespace())
                || !actual.getTenantId().equals(context.getTenantId())
                || !actual.getActorId().equals(context.getActorId())) {
            throw failure(AiIdentityError.FORBIDDEN);
        }
    }

    private static String bearer(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) { throw failure(AiIdentityError.UNAUTHENTICATED); }
        String header = headers.nextElement();
        if (headers.hasMoreElements() || header == null || !header.startsWith("Bearer ")
                || header.length() <= 7 || header.length() > 16384) {
            throw failure(AiIdentityError.VERIFICATION_FAILED);
        }
        String token = header.substring(7);
        if (token.startsWith("mcp_")) { throw failure(AiIdentityError.FORBIDDEN); }
        for (int i = 0; i < token.length(); i++) {
            char character = token.charAt(i);
            if (character <= 32 || character >= 127 || character == ',') {
                throw failure(AiIdentityError.VERIFICATION_FAILED);
            }
        }
        return token;
    }

    private static long expiry(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static AiIdentityException failure(AiIdentityError error) { return new AiIdentityException(error); }
}
