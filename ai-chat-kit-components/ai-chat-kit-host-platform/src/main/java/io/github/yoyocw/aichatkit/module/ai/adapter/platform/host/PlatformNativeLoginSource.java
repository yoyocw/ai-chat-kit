package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;

/** 从部署指定的原生包根读取当前登录，不接受请求指定类型或兼容类替身。 */
public final class PlatformNativeLoginSource {
    private static final String ROOT_PATTERN = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*";

    private final Class<?> loginType;
    private final Method getLogin;
    private final Method getTenant;
    private final Method isTenantIgnored;
    private final Method skipPermission;
    private final Method getId;
    private final Method getUserType;
    private final Method getLoginTenant;
    private final Method getAccessTokenId;
    private final Method getExpiresTime;
    private final int adminUserType;

    public static PlatformNativeLoginSource fromEnvironment(ResourceLoader resources, Environment environment) {
        if (resources == null || environment == null) { throw configuration(); }
        String root = environment.getProperty("ai-chat-kit.ai.platform-host.native-package-root");
        if (root == null || root.trim().isEmpty()) {
            root = environment.getProperty("ai-chat-kit.ai.platform-host.in-process.native-package-root");
        }
        return new PlatformNativeLoginSource(resources.getClassLoader(), root);
    }

    public PlatformNativeLoginSource(ClassLoader loader, String nativePackageRoot) {
        try {
            if (loader == null || nativePackageRoot == null || !nativePackageRoot.matches(ROOT_PATTERN)) {
                throw configuration();
            }
            loginType = type(loader, nativePackageRoot, "framework.security.core.LoginUser");
            Class<?> security = type(loader, nativePackageRoot,
                    "framework.security.core.util.SecurityFrameworkUtils");
            Class<?> tenant = type(loader, nativePackageRoot,
                    "framework.tenant.core.context.TenantContextHolder");
            Class<?> userType = type(loader, nativePackageRoot, "framework.common.enums.UserTypeEnum");
            getLogin = staticMethod(security, "getLoginUser", loginType);
            getTenant = staticMethod(tenant, "getTenantId", Long.class);
            isTenantIgnored = staticMethod(tenant, "isIgnore", boolean.class);
            skipPermission = staticMethod(security, "skipPermissionCheck", boolean.class);
            getId = instanceMethod(loginType, "getId", Long.class);
            getUserType = instanceMethod(loginType, "getUserType", Integer.class);
            getLoginTenant = instanceMethod(loginType, "getTenantId", Long.class);
            getAccessTokenId = instanceMethod(loginType, "getAccessTokenId", Long.class);
            getExpiresTime = instanceMethod(loginType, "getExpiresTime", LocalDateTime.class);
            Field admin = userType.getField("ADMIN");
            if (!Modifier.isStatic(admin.getModifiers()) || !userType.isAssignableFrom(admin.getType())) {
                throw configuration();
            }
            Integer value = (Integer) instanceMethod(userType, "getValue", Integer.class).invoke(admin.get(null));
            if (value == null) { throw configuration(); }
            adminUserType = value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            throw configuration();
        }
    }

    public NativeLogin currentLogin() {
        Object login = call(getLogin, null);
        if (login == null) { return null; }
        if (!loginType.isInstance(login)) { throw verification(); }
        return new NativeLogin((Long) call(getId, login), (Integer) call(getUserType, login),
                (Long) call(getLoginTenant, login), (Long) call(getAccessTokenId, login),
                (LocalDateTime) call(getExpiresTime, login));
    }

    public Long currentTenantId() { return (Long) call(getTenant, null); }
    public boolean tenantIgnored() { return Boolean.TRUE.equals(call(isTenantIgnored, null)); }
    public boolean skipPermissionCheck() { return Boolean.TRUE.equals(call(skipPermission, null)); }
    public int adminUserType() { return adminUserType; }

    private static Class<?> type(ClassLoader loader, String root, String suffix) throws ClassNotFoundException {
        return ClassUtils.forName(root + "." + suffix, loader);
    }

    private static Method staticMethod(Class<?> type, String name, Class<?> result, Class<?>... args)
            throws ReflectiveOperationException {
        Method method = type.getMethod(name, args);
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) { throw configuration(); }
        return method;
    }

    private static Method instanceMethod(Class<?> type, String name, Class<?> result)
            throws ReflectiveOperationException {
        Method method = type.getMethod(name);
        if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) { throw configuration(); }
        return method;
    }

    private static Object call(Method method, Object target, Object... args) {
        try { return method.invoke(target, args); }
        catch (RuntimeException | ReflectiveOperationException | LinkageError ex) { throw verification(); }
    }

    private static IllegalStateException configuration() { return new IllegalStateException("原生本地认证服务未就绪"); }
    private static AiIdentityException verification() { return new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }

    public static final class NativeLogin {
        private final Long userId;
        private final Integer userType;
        private final Long tenantId;
        private final Long accessTokenId;
        private final LocalDateTime expiresTime;

        private NativeLogin(Long userId, Integer userType, Long tenantId, Long accessTokenId,
                LocalDateTime expiresTime) {
            this.userId = userId;
            this.userType = userType;
            this.tenantId = tenantId;
            this.accessTokenId = accessTokenId;
            this.expiresTime = expiresTime;
        }

        public Long getUserId() { return userId; }
        public Integer getUserType() { return userType; }
        public Long getTenantId() { return tenantId; }
        public Long getAccessTokenId() { return accessTokenId; }
        public LocalDateTime getExpiresTime() { return expiresTime; }
    }
}
