package io.github.yoyocw.aichatkit.compat.framework.security.core.util;

import cn.hutool.core.util.StrUtil;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 服务 API Key 登录上下文工具。
 *
 * <p>业务模块通过本工具识别机器身份并读取服务端计算的数据创建人范围，不能信任请求参数中的用户范围。</p>
 */
public final class ServiceApiKeySecurityUtils {

    /** LoginUser 上下文中的服务身份标识键。 */
    public static final String CONTEXT_KEY_SERVICE_API_KEY = "serviceApiKey";

    /** 已通过本地验签的用户 JWT 标识，用于业务表启用用户数据权限；不等于固定密钥身份。 */
    public static final String CONTEXT_KEY_USER_JWT = "mcpUserJwt";

    /** LoginUser 上下文中的允许创建人编号字符串键。 */
    public static final String CONTEXT_KEY_ALLOWED_CREATOR_IDS = "serviceApiKeyAllowedCreatorIds";

    private ServiceApiKeySecurityUtils() {
    }

    /**
     * 判断当前请求是否由固定服务 API Key 完成认证。
     *
     * @return 服务 API Key 身份返回 true，普通用户或匿名请求返回 false
     */
    public static boolean isServiceApiKeyLogin() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser != null
                && Boolean.TRUE.equals(loginUser.getContext(CONTEXT_KEY_SERVICE_API_KEY, Boolean.class));
    }

    /** 判断是否为已验签的用户 JWT；匿名和固定密钥均返回 false。 */
    public static boolean isUserJwtLogin() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser != null && Boolean.TRUE.equals(
                loginUser.getContext(CONTEXT_KEY_USER_JWT, Boolean.class));
    }

    /**
     * 获取服务身份允许访问的数据创建人编号集合。
     *
     * @return 十进制用户编号字符串集合；非服务身份或范围缺失时返回空集合
     */
    public static Set<String> getAllowedCreatorIds() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null) {
            return Collections.emptySet();
        }
        String value = loginUser.getContext(CONTEXT_KEY_ALLOWED_CREATOR_IDS, String.class);
        if (StrUtil.isBlank(value)) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(StrUtil.splitTrim(value, ","));
    }

}
