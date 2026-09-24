package io.github.yoyocw.aichatkit.ai.starter.host;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;

import java.util.Objects;

/**
 * 宿主显式装配的用户认证桥接，不绑定 system、HTTP 请求头或具体登录框架。
 * namespace 必须来自部署配置；它隔离宿主身份，不等同于 JWT issuer 的信任配置。
 * 异步使用前，上游必须完成签名、issuer、audience、用途及期限校验，并建立可信宿主认证上下文。
 * 本类每次重新捕获宿主身份；传入标识及会话快照均只作为匹配条件，不构成认证证明。
 * 本类不签发或验证 JWT，不替代工具白名单、数据权限、资源归属和消息来源校验。
 */
public final class AiHostAuthenticationBridge {
    /** 当前部署固定命名空间，不能由请求覆盖。 */
    private final String namespace;
    /** 从宿主已经认证的执行上下文捕获用户身份；不得仅回显 expectedActorId。 */
    private final AiInvocationContextPort contextPort;
    /** 查询宿主真实会话源，核对有效期及注销状态。 */
    private final AiHostSessionPort sessionPort;
    /** 每次查询宿主真实权限，不提供默认通过或其他身份重试。 */
    private final AiHostPermissionPort permissionPort;

    /**
     * @param namespace 部署固定的非空命名空间
     * @param contextPort 宿主可信身份捕获实现
     * @param sessionPort 宿主真实会话核验实现
     * @param permissionPort 宿主原生权限实现
     * @throws AiIdentityException 命名空间或必要适配器缺失，类别为配置错误
     */
    public AiHostAuthenticationBridge(String namespace, AiInvocationContextPort contextPort,
                                      AiHostSessionPort sessionPort, AiHostPermissionPort permissionPort) {
        if (!hasText(namespace) || contextPort == null || sessionPort == null || permissionPort == null) {
            throw new AiIdentityException(AiIdentityError.CONFIGURATION);
        }
        this.namespace = namespace;
        this.contextPort = Objects.requireNonNull(contextPort, "宿主身份适配器不能为空");
        this.sessionPort = Objects.requireNonNull(sessionPort, "宿主会话适配器不能为空");
        this.permissionPort = Objects.requireNonNull(permissionPort, "宿主权限适配器不能为空");
    }

    /**
     * 核验当前已登录用户及其会话、权限；登录刷新后的新调用重新取得当前真实有效期。
     * @param expectedActorId 业务归属要求的用户标识，仅用于与真实登录用户比对
     * @param action 服务端入口固定选择的操作，不能由客户端选择
     * @return 真实会话快照，不含登录令牌
     * @throws IllegalStateException 身份、会话、权限无效或适配器异常；固定错误不携带原始异常
     */
    public AiHostSession authorizeCurrent(String expectedActorId, AiHostAction action) {
        try {
            AiInvocationContext context = capture(expectedActorId, action);
            AiHostSession session = sessionPort.currentSession(context);
            validateSession(context, session);
            authorize(context, action);
            // 权限源调用可能耗时，返回前再次检查会话期限。
            requireUnexpired(session);
            return session;
        } catch (AiIdentityException exception) {
            throw new AiIdentityException(exception.getError());
        } catch (RuntimeException exception) {
            throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    /**
     * 在已认证执行上下文中重新查询真实会话及权限，适用于持续执行或已验签委托的后续步骤。
     * 不会为异步线程建立登录身份；宿主无可信认证上下文时必须由 contextPort 拒绝。
     * @param expectedSession 之前核验或从已验证委托恢复的关联信息，不能作为登录凭据
     * @param action 服务端固定操作
     * @return 有效期不超过原快照及当前真实会话的较早期限，刷新不能延长旧委托
     * @throws IllegalStateException 关联、期限、身份、权限不符或真实源异常
     */
    public AiHostSession recheck(AiHostSession expectedSession, AiHostAction action) {
        try {
            requireUnexpired(expectedSession);
            AiInvocationContext context = capture(expectedSession.getActorId(), action);
            validateSession(context, expectedSession);
            AiHostSession current = sessionPort.checkSession(context, expectedSession.getSessionId());
            validateSession(context, current);
            if (!expectedSession.getSessionId().equals(current.getSessionId())) { throw denied(); }
            authorize(context, action);
            requireUnexpired(expectedSession);
            requireUnexpired(current);
            return new AiHostSession(namespace, current.getTenantId(), current.getActorId(),
                    current.getSessionId(), Math.min(expectedSession.getExpiresAtMillis(), current.getExpiresAtMillis()));
        } catch (AiIdentityException exception) {
            throw new AiIdentityException(exception.getError());
        } catch (RuntimeException exception) {
            throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    /** 捕获当前宿主认证身份，并拒绝适配器返回空字段、其他部署或其他用户。 */
    private AiInvocationContext capture(String expectedActorId, AiHostAction action) {
        if (!hasText(expectedActorId) || action == null) { throw new AiIdentityException(AiIdentityError.CONFIGURATION); }
        AiInvocationContext context = contextPort.capture(expectedActorId);
        if (context == null || !hasText(context.getTenantId()) || !hasText(context.getInvocationId())
                || !hasText(context.getNamespace()) || !hasText(context.getActorId())) {
            throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
        }
        if (!namespace.equals(context.getNamespace()) || !expectedActorId.equals(context.getActorId())) { throw denied(); }
        return context;
    }

    /** 核对会话与独立捕获的真实身份，不能只依赖快照构造器的格式检查。 */
    private void validateSession(AiInvocationContext context, AiHostSession session) {
        requireUnexpired(session);
        if (!namespace.equals(session.getNamespace()) || !context.getTenantId().equals(session.getTenantId())
                || !context.getActorId().equals(session.getActorId())) { throw denied(); }
    }

    /** 权限检查每次调用，管理员身份也不能代替操作权限。 */
    private void authorize(AiInvocationContext context, AiHostAction action) {
        if (!permissionPort.hasPermission(context, action.getPermission())) { throw denied(); }
        if (action.isPlatformAdministratorRequired() && !permissionPort.isPlatformAdministrator(context)) {
            throw denied();
        }
    }

    /** 使用 UTC Unix 毫秒严格检查，等于当前时刻即为失效。 */
    private void requireUnexpired(AiHostSession session) {
        if (session == null) { throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }
        if (session.getExpiresAtMillis() <= System.currentTimeMillis()) {
            throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED);
        }
    }

    /** 标识原样匹配，不通过 trim 改写不透明身份。 */
    private static boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }

    /** 不把适配器原始异常、请求身份或凭据写入对外错误及异常链。 */
    private static AiIdentityException denied() { return new AiIdentityException(AiIdentityError.FORBIDDEN); }
}
