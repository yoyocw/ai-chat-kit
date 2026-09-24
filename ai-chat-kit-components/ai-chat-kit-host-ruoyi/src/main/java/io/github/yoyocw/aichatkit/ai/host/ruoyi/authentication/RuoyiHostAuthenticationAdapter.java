package io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication;

import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Objects;
import java.util.UUID;

/**
 * 仅官方原生普通登录链：原 Bearer、已认证 principal、Redis 原会话及当前数据库用户必须一致。
 * 不支持自定义上游将委托兑换成同种 LoginUser；原生类型本身不能证明不存在这种改造。
 * 不建立认证上下文，不支持无当前普通 Bearer 的 MCP 资源验证或代理停止。
 */
@RequiredArgsConstructor
public final class RuoyiHostAuthenticationAdapter implements AiInvocationContextPort, AiHostSessionPort, AiHostPermissionPort {
    /** 与 starter 完全相同的部署命名空间。 */
    private final String namespace;
    /** 显式固定单租户域，不接受 HTTP 租户选择。 */
    private final String tenantId;
    /** 原宿主 token.header，不另外配置另一套验签入口。 */
    private final String tokenHeader;
    /** 原宿主 TokenService，只调用只读凭据核验方法。 */
    private final TokenService tokens;
    /** 真实 Redis 和当前用户权限复核。 */
    private final RuoyiSessionReader sessions;
    /** 服务端固定操作到已部署菜单权限的映射。 */
    private final RuoyiHostPermissions permissions;

    /** @return 原生当前用户的真实身份及新调用编号，不能使用请求 actor 参数建立身份 */
    @Override
    public AiInvocationContext captureCurrent() {
        AiHostSession actual = current().getSession();
        return context(actual);
    }

    /** @param expectedActorId 仅归属匹配条件 @return 同一真实当前用户的调用上下文 */
    @Override
    public AiInvocationContext capture(String expectedActorId) {
        AiHostSession actual = current().getSession();
        if (!actual.getActorId().equals(expectedActorId)) { throw forbidden(); }
        return context(actual);
    }

    /** @param context 先前捕获的归属条件 @return 重新核验的原生当前会话 */
    @Override
    public AiHostSession currentSession(AiInvocationContext context) {
        AiHostSession actual = current().getSession();
        match(context, actual);
        return actual;
    }

    /** @param context 原归属 @param sessionId 原 UUID 只作匹配 @return 当前原会话，不创建无登录身份 */
    @Override
    public AiHostSession checkSession(AiInvocationContext context, String sessionId) {
        AiHostSession actual = currentSession(context);
        if (!actual.getSessionId().equals(sessionId)) { throw forbidden(); }
        return actual;
    }

    /** @param context 本轮归属 @param permission 服务端操作码 @return 当前数据库菜单权限是否明确允许 */
    @Override
    public boolean hasPermission(AiInvocationContext context, String permission) {
        String required = permissions.resolve(permission);
        RuoyiVerifiedSession actual = current();
        match(context, actual.getSession());
        // 沿用若依 @ss.hasPermi 的全权或精确匹配，不额外增加模式通配。
        return actual.getPermissions().contains(Constants.ALL_PERMISSION) || actual.getPermissions().contains(required);
    }

    /** @param context 当前归属 @return 已复核活动用户是否满足原若依管理员规则，不替换实际用户 */
    @Override
    public boolean isPlatformAdministrator(AiInvocationContext context) {
        RuoyiVerifiedSession actual = current();
        match(context, actual.getSession());
        return actual.isAdministrator();
    }

    /** 当前原生凭据和 principal 必须由原 TokenService 正面绑定，空结果保留未知失败分类。 */
    private RuoyiVerifiedSession current() {
        try {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) { throw unauthenticated(); }
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            validateBearer(request);
            LoginUser principal = principal();
            LoginUser verified = tokens.getLoginUser(request);
            // 原 API 将 Redis/解析故障也折叠成 null，不能据此断言过期或源不可用。
            if (verified == null) { throw failed(); }
            if (!Objects.equals(principal.getUserId(), verified.getUserId())
                    || !Objects.equals(principal.getToken(), verified.getToken())) { throw forbidden(); }
            if (verified.getExpireTime() == null || principal.getExpireTime() == null) { throw failed(); }
            // 不修改宿主可变 LoginUser；读者先取两份真实期限中的较早值。
            return sessions.read(verified, principal.getExpireTime());
        } catch (AiIdentityException ex) {
            throw new AiIdentityException(ex.getError());
        } catch (RuntimeException ex) {
            throw failed();
        }
    }

    /** 只接受官方过滤器建立的原生类型；存在 Bearer 但上下文缺失时不猜测上游失败原因。 */
    private LoginUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) { throw failed(); }
        if (authentication.getClass() != UsernamePasswordAuthenticationToken.class || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof WebAuthenticationDetails)
                || authentication.getPrincipal() == null || authentication.getPrincipal().getClass() != LoginUser.class) {
            throw forbidden();
        }
        LoginUser user = (LoginUser) authentication.getPrincipal();
        if (user.getUserId() == null || user.getUserId() <= 0 || user.getToken() == null || user.getToken().isEmpty()
                || user.getUser() == null || !Objects.equals(user.getUserId(), user.getUser().getUserId())) { throw failed(); }
        return user;
    }

    /** 仅使用宿主实际 token.header；歧义凭据拒绝，MCP 不能成为普通用户证明。 */
    private void validateBearer(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(tokenHeader);
        if (headers == null || !headers.hasMoreElements()) { throw unauthenticated(); }
        String value = headers.nextElement();
        if (headers.hasMoreElements() || value == null || !value.startsWith("Bearer ")
                || value.length() <= 7 || value.length() > 16384) { throw failed(); }
        if (value.substring(7).startsWith("mcp_")) { throw forbidden(); }
        for (int i = 7; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character <= 32 || character >= 127 || character == ',') { throw failed(); }
        }
    }

    /** 不通过字符串转换修复或改变已验证的真实用户编号。 */
    private AiInvocationContext context(AiHostSession actual) {
        return new AiInvocationContext(namespace, tenantId, actual.getActorId(), UUID.randomUUID().toString());
    }

    /** 拒绝跨部署、跨单租户域或其他用户；调用编号只是关联，不是身份凭据。 */
    private void match(AiInvocationContext expected, AiHostSession actual) {
        if (expected == null || expected.getInvocationId() == null || expected.getInvocationId().trim().isEmpty()) { throw failed(); }
        if (!namespace.equals(expected.getNamespace()) || !tenantId.equals(expected.getTenantId())
                || !actual.getActorId().equals(expected.getActorId())) { throw forbidden(); }
    }

    /** @return 明确缺少普通请求凭据 */
    private static AiIdentityException unauthenticated() { return new AiIdentityException(AiIdentityError.UNAUTHENTICATED); }
    /** @return 明确模式或归属拒绝 */
    private static AiIdentityException forbidden() { return new AiIdentityException(AiIdentityError.FORBIDDEN); }
    /** @return 不携带宿主异常或凭据的未知核验失败 */
    private static AiIdentityException failed() { return new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }
}
