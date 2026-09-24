package io.github.yoyocw.aichatkit.compat.framework.security.core.filter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.ServiceApiKeyCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.UserTypeEnum;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.compat.framework.common.util.servlet.ServletUtils;
import io.github.yoyocw.aichatkit.compat.framework.security.config.SecurityProperties;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.McpServiceJwtCodec;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.McpUserSessionValidator;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.ServiceApiKeySecurityUtils;
import io.github.yoyocw.aichatkit.compat.framework.web.core.util.WebFrameworkUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.github.yoyocw.aichatkit.compat.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;

/**
 * MCP 固定服务密钥与短期 JWT 认证过滤器。
 *
 * <p>固定密钥通过 AI 服务查库认证，JWT 使用本服务公钥验签；两者共用端点、租户和数据范围限制。</p>
 */
@RequiredArgsConstructor
@Slf4j
public class ServiceApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /** 固定密钥沿用原有 Base64URL 格式和长度限制，避免异常输入进入远程鉴权。 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^mcp_[A-Za-z0-9_-]{43,252}$");

    /** 固定密钥内部认证 API，统一校验数据库状态、有效期和服务账号数据范围。 */
    private final ServiceApiKeyCommonApi serviceApiKeyApi;

    /** 通用安全配置，用于读取项目统一 Authorization Header 名称。 */
    private final SecurityProperties securityProperties;

    /** MCP 服务 JWT 编解码器，使用本地公钥完成离线验签和身份展开。 */
    private final McpServiceJwtCodec jwtCodec;

    /** 用户 JWT 会话有效性检查，刷新或退出后的旧访问令牌记录必须立即拒绝。 */
    private final McpUserSessionValidator sessionValidator;

    /** 当前业务微服务由 Controller 注解生成的不可变端点注册表。 */
    private final McpServiceApiEndpointRegistry endpointRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = obtainBearerToken(request);
        if (token == null || !token.startsWith("mcp_")) {
            chain.doFilter(request, response);
            return;
        }
        ServiceApiKeyEndpoint endpoint = findEndpoint(request.getMethod(), getRequestPath(request));
        if (!token.startsWith(McpServiceJwtCodec.TOKEN_PREFIX) && !TOKEN_PATTERN.matcher(token).matches()) {
            log.warn("[authenticate][fixed-key rejected reason=TOKEN_FORMAT length={}]", token.length());
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHORIZED.getCode(), "服务凭据无效");
            return;
        }
        if (endpoint == null) {
            reject(request, response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN.getCode(), "服务凭据无权访问该接口");
            return;
        }
        ServiceApiKeyAuthRespDTO identity = authenticate(request, response, token);
        if (identity == null || !validateEndpointPermission(request, response, endpoint, identity)
                || !validateTenant(request, response, identity)
                || !validatePageSize(request, response, endpoint, identity.getMaxPageSize())) {
            return;
        }
        // 用户 JWT 沿用普通登录用户的数据权限链；固定密钥继续使用原有创建人范围。
        SecurityFrameworkUtils.setLoginUser(buildLoginUser(identity,
                token.startsWith(McpServiceJwtCodec.TOKEN_PREFIX)), request);
        chain.doFilter(request, response);
    }

    /** 按凭据类型认证；JWT 验签失败不会回退到固定密钥认证，异常时拒绝访问。 */
    private ServiceApiKeyAuthRespDTO authenticate(HttpServletRequest request, HttpServletResponse response, String token) {
        try {
            ServiceApiKeyAuthRespDTO identity;
            if (token.startsWith(McpServiceJwtCodec.TOKEN_PREFIX)) {
                // 先验签再查询会话，不接受仅凭记录 ID 发起的外部身份声明。
                identity = jwtCodec.verify(token);
                sessionValidator.validate(identity.getAccessTokenId(), identity.getServiceUserId(),
                        identity.getTenantId(), identity.getExpiresAtEpochSecond());
                return identity;
            } else {
                // 仅发送完整密钥的 SHA-256 摘要，复用数据库有效期、启停和数据权限校验。
                ServiceApiKeyAuthReqDTO authRequest = new ServiceApiKeyAuthReqDTO();
                authRequest.setKeySha256(DigestUtil.sha256Hex(token));
                identity = serviceApiKeyApi.authenticate(authRequest).getCheckedData();
            }
            if (identity != null && identity.getAllowedCreatorIds() != null
                    && !identity.getAllowedCreatorIds().isEmpty()) {
                return identity;
            }
            log.warn("[authenticate][fixed-key rejected reason={}]",
                    identity == null ? "AUTH_RESULT_EMPTY" : "CREATOR_SCOPE_EMPTY" );
        } catch (Throwable ex) {
            log.warn("[authenticate][MCP 服务身份加载失败 method={} path={} ip={}]",
                    request.getMethod(), getRequestPath(request), ServletUtils.getClientIP(request), ex);
        }
        reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHORIZED.getCode(), "服务凭据无效");
        return null;
    }

    /** 从项目统一 Authorization Header 中提取 Bearer Token。 */
    private String obtainBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(securityProperties.getTokenHeader());
        String prefix = SecurityFrameworkUtils.AUTHORIZATION_BEARER + " ";
        return StrUtil.startWith(authorization, prefix) ? StrUtil.trim(authorization.substring(prefix.length())) : null;
    }

    /** 获取去除 ContextPath 后用于精确白名单匹配的请求路径。 */
    private String getRequestPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** 按 HTTP 方法和完整路径查找代码注解声明的精确开放端点。 */
    private ServiceApiKeyEndpoint findEndpoint(String method, String path) {
        return endpointRegistry.find(method, path);
    }

    /** 校验数据库或 JWT 授权只能在 Controller 注解定义的开放上限内缩小访问范围。 */
    private boolean validateEndpointPermission(HttpServletRequest request, HttpServletResponse response,
                                               ServiceApiKeyEndpoint endpoint, ServiceApiKeyAuthRespDTO identity) {
        if (identity.getAllowedEndpointCodes() != null
                && identity.getAllowedEndpointCodes().contains(endpoint.getEndpointCode())) {
            return true;
        }
        reject(request, response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN.getCode(), "服务凭据无权访问该接口");
        return false;
    }

    /** 校验可选外部租户头不能覆盖认证身份绑定租户。 */
    private boolean validateTenant(HttpServletRequest request, HttpServletResponse response,
                                   ServiceApiKeyAuthRespDTO identity) {
        Long requestTenantId = WebFrameworkUtils.getTenantId(request);
        if (requestTenantId == null || requestTenantId.equals(identity.getTenantId())) {
            return true;
        }
        reject(request, response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN.getCode(), "服务身份不能切换租户");
        return false;
    }

    /** 校验分页接口的 pageSize 不超过认证身份的分页上限。 */
    private boolean validatePageSize(HttpServletRequest request, HttpServletResponse response,
                                     ServiceApiKeyEndpoint endpoint, Integer maxPageSize) {
        String pageSizeValue = request.getParameter("pageSize");
        if (!endpoint.isPageable() || StrUtil.isBlank(pageSizeValue)) {
            return true;
        }
        try {
            int pageSize = Integer.parseInt(pageSizeValue);
            if (maxPageSize != null && pageSize >= 1 && pageSize <= maxPageSize) {
                return true;
            }
        } catch (NumberFormatException ignored) {
            // 外部解析异常统一转换为安全的参数错误，不回显异常细节。
        }
        reject(request, response, HttpServletResponse.SC_BAD_REQUEST, BAD_REQUEST.getCode(),
                "pageSize 必须在 1 到 " + maxPageSize + " 之间");
        return false;
    }

    /** 用户 JWT 不设置固定密钥标记，交由现有部门/角色权限规则过滤；固定密钥保持创建人约束。 */
    private LoginUser buildLoginUser(ServiceApiKeyAuthRespDTO identity, boolean userJwt) {
        LoginUser loginUser = new LoginUser().setId(identity.getServiceUserId())
                .setUserType(UserTypeEnum.ADMIN.getValue()).setTenantId(identity.getTenantId())
                .setScopes(Collections.emptyList()).setInfo(buildUserInfo(identity));
        if (userJwt) {
            // 内部调用继续关联同一访问令牌记录，不能丢失已验证的会话身份。
            loginUser.setAccessTokenId(identity.getAccessTokenId());
            loginUser.setContext(ServiceApiKeySecurityUtils.CONTEXT_KEY_USER_JWT, true);
            loginUser.setAccessTokenId(identity.getAccessTokenId());
            loginUser.setExpiresTime(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(identity.getExpiresAtEpochSecond()), ZoneId.systemDefault()));
            return loginUser;
        }
        loginUser.setContext(ServiceApiKeySecurityUtils.CONTEXT_KEY_SERVICE_API_KEY, true);
        loginUser.setContext(ServiceApiKeySecurityUtils.CONTEXT_KEY_ALLOWED_CREATOR_IDS,
                joinUserIds(identity.getAllowedCreatorIds()));
        return loginUser;
    }

    /** 组装仅用于审计展示的昵称和部门上下文。 */
    private Map<String, String> buildUserInfo(ServiceApiKeyAuthRespDTO identity) {
        Map<String, String> info = new HashMap<>();
        if (StrUtil.isNotBlank(identity.getNickname())) {
            info.put(LoginUser.INFO_KEY_NICKNAME, identity.getNickname());
        }
        if (identity.getDeptId() != null) {
            info.put(LoginUser.INFO_KEY_DEPT_ID, String.valueOf(identity.getDeptId()));
        }
        return info;
    }

    /** 将允许创建人编号集合序列化到 LoginUser 字符串上下文。 */
    private String joinUserIds(Set<Long> userIds) {
        StringBuilder value = new StringBuilder();
        for (Long userId : userIds) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(userId);
        }
        return value.toString();
    }

    /** 返回统一错误结构并禁止中间节点缓存鉴权失败响应。 */
    private void reject(HttpServletRequest request, HttpServletResponse response,
                        int httpStatus, int errorCode, String message) {
        log.warn("[reject][MCP 服务身份请求被拒绝 method={} path={} ip={} status={}]",
                request.getMethod(), getRequestPath(request), ServletUtils.getClientIP(request), httpStatus);
        response.setStatus(httpStatus);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        ServletUtils.writeJSON(response, CommonResult.error(errorCode, message));
    }
}
