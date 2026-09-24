package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 只从可信部署包根定位宿主原生实现与接口；调用始终经过 Spring 代理。
 * 组件改名后的兼容类绝不能作为原宿主认证源的替代品。
 */
public final class PlatformLocalBeanResolver {
    private static final String TOKEN_BEAN = "oauth2TokenApiImpl";
    private static final String PERMISSION_BEAN = "permissionApiImpl";
    private static final String ROOT_PATTERN = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*";

    private final Object tokens;
    private final Object permissions;
    private final Method tokenCheck;
    private final Method sessionCheck;
    private final Method permissionCheck;
    private final Method roleCheck;
    private final Method getLogin;
    private final Method getTenant;
    private final Method isTenantIgnored;
    private final Method skipPermission;
    private final Method resultCode;
    private final Method resultData;
    private final Method resultSuccess;
    private final Accessors loginAccessors;
    private final Accessors tokenAccessors;
    private final Class<?> loginType;
    private final Class<?> tokenType;
    private final Class<?> resultType;
    private final Integer adminUserType;

    /** 启动时校验完整 ABI；包根只来自部署配置，绝不由请求指定。 */
    public PlatformLocalBeanResolver(BeanFactory beans, ClassLoader loader, String nativePackageRoot) {
        try {
            if (beans == null || loader == null || nativePackageRoot == null
                    || !nativePackageRoot.matches(ROOT_PATTERN)) { throw configuration(); }
            Class<?> tokenApi = type(loader, nativePackageRoot, "framework.common.biz.system.oauth2.OAuth2TokenCommonApi");
            Class<?> permissionApi = type(loader, nativePackageRoot, "framework.common.biz.system.permission.PermissionCommonApi");
            resultType = type(loader, nativePackageRoot, "framework.common.pojo.CommonResult");
            loginType = type(loader, nativePackageRoot, "framework.security.core.LoginUser");
            tokenType = type(loader, nativePackageRoot,
                    "framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO");
            Class<?> security = type(loader, nativePackageRoot,
                    "framework.security.core.util.SecurityFrameworkUtils");
            Class<?> tenant = type(loader, nativePackageRoot,
                    "framework.tenant.core.context.TenantContextHolder");
            Class<?> userType = type(loader, nativePackageRoot, "framework.common.enums.UserTypeEnum");
            tokens = local(beans, TOKEN_BEAN, tokenApi, type(loader, nativePackageRoot,
                    "module.system.api.oauth2.OAuth2TokenApiImpl"));
            permissions = local(beans, PERMISSION_BEAN, permissionApi, type(loader, nativePackageRoot,
                    "module.system.api.permission.PermissionApiImpl"));

            tokenCheck = endpoint(tokenApi, tokens, "checkAccessToken", resultType, String.class);
            sessionCheck = endpoint(tokenApi, tokens, "checkAccessTokenSession", resultType, Long.class);
            permissionCheck = endpoint(permissionApi, permissions, "hasAnyPermissions",
                    resultType, Long.class, String[].class);
            roleCheck = endpoint(permissionApi, permissions, "hasAnyRoles",
                    resultType, Long.class, String[].class);
            getLogin = staticMethod(security, "getLoginUser", loginType);
            skipPermission = staticMethod(security, "skipPermissionCheck", boolean.class);
            getTenant = staticMethod(tenant, "getTenantId", Long.class);
            isTenantIgnored = staticMethod(tenant, "isIgnore", boolean.class);
            resultCode = instanceMethod(resultType, "getCode", Integer.class);
            resultData = instanceMethod(resultType, "getData", Object.class);
            resultSuccess = staticMethod(resultType, "isSuccess", boolean.class, Integer.class);
            loginAccessors = new Accessors(loginType);
            tokenAccessors = new Accessors(tokenType);
            Field admin = userType.getField("ADMIN");
            if (!Modifier.isStatic(admin.getModifiers()) || !userType.isAssignableFrom(admin.getType())) {
                throw configuration();
            }
            adminUserType = (Integer) instanceMethod(userType, "getValue", Integer.class).invoke(admin.get(null));
            if (adminUserType == null) { throw configuration(); }
        } catch (RuntimeException | ReflectiveOperationException | LinkageError ex) {
            throw configuration();
        }
    }

    public NativeIdentity currentLogin() {
        Object login = call(getLogin, null);
        return login == null ? null : snapshot(login, loginType, loginAccessors);
    }

    public NativeIdentity checkAccessToken(String raw) {
        return token(call(tokenCheck, tokens, raw));
    }

    public NativeIdentity checkAccessTokenSession(Long accessTokenId) {
        return token(call(sessionCheck, tokens, accessTokenId));
    }

    public boolean hasAnyPermissions(Long userId, String permission) {
        return Boolean.TRUE.equals(booleanResult(call(permissionCheck, permissions,
                userId, new String[]{permission})));
    }

    public boolean hasAnyRoles(Long userId, String role) {
        return Boolean.TRUE.equals(booleanResult(call(roleCheck, permissions,
                userId, new String[]{role})));
    }

    public Long currentTenantId() { return (Long) call(getTenant, null); }

    public boolean tenantIgnored() { return Boolean.TRUE.equals(call(isTenantIgnored, null)); }

    public boolean skipPermissionCheck() { return Boolean.TRUE.equals(call(skipPermission, null)); }

    public Integer adminUserType() { return adminUserType; }

    private NativeIdentity token(Object result) {
        Object value = checkedResult(result);
        return snapshot(value, tokenType, tokenAccessors);
    }

    private Boolean booleanResult(Object result) {
        Object value = checkedResult(result);
        if (!(value instanceof Boolean)) { throw verification(); }
        return (Boolean) value;
    }

    private Object checkedResult(Object result) {
        if (result == null || !resultType.isInstance(result)) { throw verification(); }
        Object code = call(resultCode, result);
        if (!(code instanceof Integer) || !Boolean.TRUE.equals(call(resultSuccess, null, code))) {
            throw verification();
        }
        Object data = call(resultData, result);
        if (data == null) { throw verification(); }
        return data;
    }

    private static NativeIdentity snapshot(Object value, Class<?> type, Accessors accessors) {
        if (value == null || !type.isInstance(value)) { throw verification(); }
        return new NativeIdentity((Long) call(accessors.userId, value),
                (Long) call(accessors.tenantId, value),
                (Integer) call(accessors.userType, value),
                (Long) call(accessors.accessTokenId, value),
                (LocalDateTime) call(accessors.expiresTime, value));
    }

    /** Method 来自原接口，invoke 的对象仍是原代理，不绕过租户/权限切面。 */
    private static Method endpoint(Class<?> api, Object proxy, String name, Class<?> result, Class<?>... args)
            throws ReflectiveOperationException {
        Method contract = api.getMethod(name, args);
        Class<?> target = AopUtils.getTargetClass(proxy);
        Method implementation = Objects.requireNonNull(target).getMethod(name, args);
        if (!api.isInterface() || contract.getReturnType() != result
                || Modifier.isStatic(contract.getModifiers())
                || implementation.getReturnType() != result
                || implementation.getDeclaringClass().isInterface()
                || Modifier.isAbstract(implementation.getModifiers())
                || Modifier.isStatic(implementation.getModifiers())) { throw configuration(); }
        return contract;
    }

    private static Object local(BeanFactory beans, String name, Class<?> api, Class<?> implementation) {
        Object bean = beans.getBean(name);
        Class<?> target = AopUtils.getTargetClass(bean);
        if (!api.isInterface() || !api.isInstance(bean) || target == null
                || !target.equals(implementation) || !api.isAssignableFrom(target)) {
            throw configuration();
        }
        return bean;
    }

    private static Class<?> type(ClassLoader loader, String root, String suffix) throws ClassNotFoundException {
        return ClassUtils.forName(root + "." + suffix, loader);
    }

    private static Method staticMethod(Class<?> type, String name, Class<?> result, Class<?>... args)
            throws ReflectiveOperationException {
        Method method = type.getMethod(name, args);
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) {
            throw configuration();
        }
        return method;
    }

    private static Method instanceMethod(Class<?> type, String name, Class<?> result)
            throws ReflectiveOperationException {
        Method method = type.getMethod(name);
        if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) {
            throw configuration();
        }
        return method;
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (RuntimeException | ReflectiveOperationException | LinkageError ex) {
            // InvocationTargetException 的 cause 可能含凭据或服务响应，不向外传播。
            throw verification();
        }
    }

    private static IllegalStateException configuration() {
        return new IllegalStateException("原生本地认证服务未就绪");
    }

    private static AiIdentityException verification() {
        return new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
    }

    private static final class Accessors {
        private final Method userId;
        private final Method tenantId;
        private final Method userType;
        private final Method accessTokenId;
        private final Method expiresTime;

        private Accessors(Class<?> type) throws ReflectiveOperationException {
            this.userId = instanceMethod(type, type.getSimpleName().equals("LoginUser") ? "getId" : "getUserId",
                    Long.class);
            this.tenantId = instanceMethod(type, "getTenantId", Long.class);
            this.userType = instanceMethod(type, "getUserType", Integer.class);
            this.accessTokenId = instanceMethod(type, "getAccessTokenId", Long.class);
            this.expiresTime = instanceMethod(type, "getExpiresTime", LocalDateTime.class);
        }
    }

    /** 反射边界只输出组件自有的不可变身份快照。 */
    public static final class NativeIdentity {
        private final Long userId;
        private final Long tenantId;
        private final Integer userType;
        private final Long accessTokenId;
        private final LocalDateTime expiresTime;

        private NativeIdentity(Long userId, Long tenantId, Integer userType, Long accessTokenId,
                LocalDateTime expiresTime) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.userType = userType;
            this.accessTokenId = accessTokenId;
            this.expiresTime = expiresTime;
        }

        public Long getUserId() { return userId; }
        public Long getTenantId() { return tenantId; }
        public Integer getUserType() { return userType; }
        public Long getAccessTokenId() { return accessTokenId; }
        public LocalDateTime getExpiresTime() { return expiresTime; }
    }
}
