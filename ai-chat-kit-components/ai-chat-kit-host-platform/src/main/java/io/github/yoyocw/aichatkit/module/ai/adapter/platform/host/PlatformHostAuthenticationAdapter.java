package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.UserTypeEnum;
import io.github.yoyocw.aichatkit.compat.framework.common.util.servlet.ServletUtils;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiDelegatedCallContext;

import javax.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.UUID;

/**
 * 平台可选普通 Bearer 接入，身份、会话、权限及来源共用真实 USER/TOKEN 复核。
 * 仅支持 inspection 固定主体租户的后台用户；不能用于委托、header-only、模拟登录或异步认证。
 * session/permission 均依赖当前 HTTP 普通 Bearer，不能用作 MCP 资源验签侧的无登录上下文适配。
 * 宿主不得在到达本适配前将委托兑换或改写成普通用户凭据；已丢失来源时不适用。
 */
public final class PlatformHostAuthenticationAdapter
        implements AiInvocationContextPort, AiHostSessionPort, AiHostPermissionPort {
    /** 平台原执行链的固定命名空间，不接受请求覆盖。 */
    private static final String NAMESPACE = "platform";
    /** 固定受限认证源，不访问 system 数据表。 */
    private final AiSessionInspectionClient inspection;
    /** 复用已启用的固定主体租户配置。 */
    private final AiSessionInspectionProperties properties;
    /** 多租户模式按当前可信登录租户选择部署绑定；与 fixed legacy 模式二选一。 */
    private final PlatformTenantInspectionRouter tenantRouter;

    /** 保留原固定主体租户构造及语义。 */
    public PlatformHostAuthenticationAdapter(AiSessionInspectionClient inspection,
            AiSessionInspectionProperties properties) {
        this.inspection = inspection;
        this.properties = properties;
        this.tenantRouter = null;
    }

    /** 标准平台多租户接入只消费部署绑定，不要求宿主编写端口实现。 */
    public PlatformHostAuthenticationAdapter(PlatformTenantInspectionRouter tenantRouter) {
        if (tenantRouter == null) { throw new AiIdentityException(AiIdentityError.CONFIGURATION); }
        this.inspection = null;
        this.properties = null;
        this.tenantRouter = tenantRouter;
    }

    /** 从原始普通用户凭据复核身份；expectedActorId 仅作归属匹配，不提供登录能力。 */
    @Override
    public AiInvocationContext capture(String expectedActorId) {
        OAuth2SessionInspectionRespDTO identity = inspect(null, false);
        if (!identity.getUserId().toString().equals(expectedActorId)) { throw denied(); }
        return new AiInvocationContext(NAMESPACE, identity.getTenantId().toString(), expectedActorId,
                UUID.randomUUID().toString());
    }

    /** 当前用户来自原始 Bearer 的真实 USER/TOKEN 复核，不以调用者参数指定身份。 */
    @Override
    public AiInvocationContext captureCurrent() {
        OAuth2SessionInspectionRespDTO identity = inspect(null, false);
        return new AiInvocationContext(NAMESPACE, identity.getTenantId().toString(), identity.getUserId().toString(),
                UUID.randomUUID().toString());
    }

    /** @return 与本轮身份匹配的真实会话；到期时间不超过当前登录快照 */
    @Override
    public AiHostSession currentSession(AiInvocationContext context) {
        OAuth2SessionInspectionRespDTO identity = inspect(null, false);
        match(context, identity);
        return session(identity);
    }

    /** 原始普通 Bearer 仍须存在；sessionId 仅作匹配，不能用于资源验签或建立委托身份。 */
    @Override
    public AiHostSession checkSession(AiInvocationContext context, String sessionId) {
        AiHostSession actual = currentSession(context);
        if (!actual.getSessionId().equals(sessionId)) { throw denied(); }
        return actual;
    }

    /** 当前 HTTP 普通身份下查询固定平台权限；不支持无普通 Bearer 的资源侧权限复核。 */
    @Override
    public boolean hasPermission(AiInvocationContext context, String permission) {
        OAuth2SessionInspectionRespDTO identity = inspect(PlatformHostPermissions.resolve(permission), false);
        match(context, identity);
        return identity.isPermissionsSatisfied();
    }

    /** 由认证侧受控断言核验真实平台身份，不从用户类型或固定租户推断管理员。 */
    @Override
    public boolean isPlatformAdministrator(AiInvocationContext context) {
        OAuth2SessionInspectionRespDTO identity = inspect(null, true);
        match(context, identity);
        if (identity.getPlatformAdministrator() == null) { throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }
        return Boolean.TRUE.equals(identity.getPlatformAdministrator());
    }

    /**
     * 只有本次原始普通用户凭据正面核验通过才表示无委托来源；缺少委托标记不是证明。
     * @param appId 引擎确定的实际应用；应用授权仍由独立授权端口执行
     * @return 普通用户没有机器委托来源，返回 null
     */
    public AiCallerOrigin captureOrdinaryOrigin(String appId) {
        if (appId == null || appId.trim().isEmpty()) { throw new AiIdentityException(AiIdentityError.CONFIGURATION); }
        inspect(null, false);
        return null;
    }

    /** 每次从当前请求读原凭据并执行 TOKEN 查询，禁止退化为 sessionId 查询或缓存身份。 */
    private OAuth2SessionInspectionRespDTO inspect(String permission, boolean administrator) {
        try {
            HttpServletRequest request = ServletUtils.getRequest();
            if (request == null) { throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED); }
            if (request.getAttribute(AiDelegatedCallContext.class.getName()) != null) { throw denied(); }
            LoginUser user = SecurityFrameworkUtils.getLoginUser();
            validateLogin(user);
            OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
            query.setSubjectType("USER");
            query.setAccessToken(bearer(request));
            query.setExpectedTenantId(subjectTenant(user));
            query.setExpectedUserId(user.getId());
            query.setRequirePlatformAdministrator(administrator);
            if (permission != null) { query.setRequiredPermissions(Collections.singletonList(permission)); }
            OAuth2SessionInspectionRespDTO identity = tenantRouter == null
                    ? inspection.inspectWithPermissionResult(query)
                    : tenantRouter.inspectWithPermissionResult(query);
            validateIdentity(user, identity);
            return identity;
        } catch (AiIdentityException ex) {
            throw new AiIdentityException(ex.getError());
        } catch (RuntimeException ex) {
            // 未知故障不代表用户未登录；丢弃原异常内容与异常链。
            throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
        }
    }

    /** 必须是已认证后台用户，且登录租户与当前执行租户、固定部署一致。 */
    private void validateLogin(LoginUser user) {
        if (user == null) { throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED); }
        if (user.getId() == null || user.getId() <= 0
                || user.getAccessTokenId() == null || user.getAccessTokenId() <= 0
                || user.getExpiresTime() == null) { throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }
        if (loginExpiry(user) <= System.currentTimeMillis()) { throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED); }
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType())
                || !Objects.equals(user.getTenantId(), TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore()) { throw denied(); }
        if (tenantRouter == null && (properties == null || !properties.isEnabled()
                || properties.getSubjectTenantId() == null || properties.getSubjectTenantId() < 0)) {
            throw new AiIdentityException(AiIdentityError.CONFIGURATION);
        }
        if (tenantRouter == null && !Objects.equals(user.getTenantId(), properties.getSubjectTenantId())) {
            throw denied();
        }
    }

    /** 多租户取已校验登录租户；legacy 仍使用固定部署租户。 */
    private Long subjectTenant(LoginUser user) {
        return tenantRouter == null ? properties.getSubjectTenantId() : user.getTenantId();
    }

    /** 检查令牌实际会话与 LoginUser 完整身份；真实源不能返回另一个有效用户会话。 */
    private void validateIdentity(LoginUser user, OAuth2SessionInspectionRespDTO identity) {
        if (identity == null || identity.getExpiresAtMillis() == null) {
            throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
        }
        if (!Objects.equals(user.getId(), identity.getUserId())
                || !Objects.equals(user.getTenantId(), identity.getTenantId())
                || !Objects.equals(user.getUserType(), identity.getUserType())
                || !Objects.equals(user.getAccessTokenId(), identity.getSessionId())) { throw denied(); }
        if (identity.getExpiresAtMillis() <= System.currentTimeMillis() || loginExpiry(user) <= System.currentTimeMillis()) {
            throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED);
        }
    }

    /** 仅接受一个标准 Authorization Bearer；拒绝参数、透传身份、MCP 凭据及歧义头。 */
    private String bearer(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) { throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED); }
        String value = headers.nextElement();
        if (headers.hasMoreElements() || value == null || !value.startsWith("Bearer ")
                || value.length() <= 7 || value.length() > 16384) { throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }
        String token = value.substring(7);
        if (token.startsWith("mcp_")) { throw denied(); }
        for (int i = 0; i < token.length(); i++) {
            char character = token.charAt(i);
            if (character <= 32 || character >= 127 || character == ',') { throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }
        }
        return token;
    }

    /** 校验内部本轮身份，不解析不透明编号，不接受跨命名空间、租户或用户快照。 */
    private void match(AiInvocationContext context, OAuth2SessionInspectionRespDTO identity) {
        if (context == null || context.getInvocationId() == null || context.getInvocationId().trim().isEmpty()) {
            throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
        }
        if (!NAMESPACE.equals(context.getNamespace())
                || !identity.getTenantId().toString().equals(context.getTenantId())
                || !identity.getUserId().toString().equals(context.getActorId())) { throw denied(); }
    }

    /** 使用真实 UTC 毫秒期限，并受当前登录快照限制，不通过复核刷新旧登录寿命。 */
    private AiHostSession session(OAuth2SessionInspectionRespDTO identity) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        validateLogin(user);
        validateIdentity(user, identity);
        return new AiHostSession(NAMESPACE, identity.getTenantId().toString(), identity.getUserId().toString(),
                identity.getSessionId().toString(), Math.min(identity.getExpiresAtMillis(), loginExpiry(user)));
    }

    /** 平台 LoginUser 沿用宿主本地时区语义，不将其当作跨宿主通用时间协议。 */
    private long loginExpiry(LoginUser user) { return user.getExpiresTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    /** @return 不带凭据、身份或底层异常的固定拒绝信息 */
    private static AiIdentityException denied() { return new AiIdentityException(AiIdentityError.FORBIDDEN); }
}
