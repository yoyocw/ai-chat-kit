package io.github.yoyocw.aichatkit.testnative.framework.security.core.util;

import io.github.yoyocw.aichatkit.testnative.framework.security.core.LoginUser;

/** Test-only thread-scoped native security entry points. */
public final class SecurityFrameworkUtils {
    private static final ThreadLocal<LoginUser> USER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SKIP = new ThreadLocal<>();
    private SecurityFrameworkUtils() { }
    public static LoginUser getLoginUser() { return USER.get(); }
    public static void setLoginUser(LoginUser value) { USER.set(value); }
    public static boolean skipPermissionCheck() { return Boolean.TRUE.equals(SKIP.get()); }
    public static void setSkipPermissionCheck(boolean value) { SKIP.set(value); }
    public static void clear() { USER.remove(); SKIP.remove(); }
}
