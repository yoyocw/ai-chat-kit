package io.github.yoyocw.aichatkit.testnative.framework.tenant.core.context;

/** Test-only native tenant context. */
public final class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE = new ThreadLocal<>();
    private TenantContextHolder() { }
    public static Long getTenantId() { return TENANT.get(); }
    public static void setTenantId(Long value) { TENANT.set(value); }
    public static boolean isIgnore() { return Boolean.TRUE.equals(IGNORE.get()); }
    public static void setIgnore(boolean value) { IGNORE.set(value); }
    public static void clear() { TENANT.remove(); IGNORE.remove(); }
}
