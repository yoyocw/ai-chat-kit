package io.github.yoyocw.aichatkit.compat.framework.security.core.filter;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MCP 服务 API Key 可访问的注解端点元数据。
 *
 * <p>方法和路径必须同时匹配，避免服务账号角色误配后进入未授权接口。</p>
 */
@Getter
@AllArgsConstructor
public class ServiceApiKeyEndpoint {

    /** 全部 MCP 业务服务中稳定且唯一的数据库授权编码。 */
    private final String endpointCode;

    /** HTTP 方法，当前只允许 GET。 */
    private final String method;

    /** 包含 API 前缀的规范化请求路径，不允许使用通配符。 */
    private final String path;

    /** 是否为分页接口；分页接口额外校验 pageSize 上限。 */
    private final boolean pageable;

    /**
     * 判断请求方法和路径是否与当前白名单端点完全一致。
     *
     * @param requestMethod 请求 HTTP 方法
     * @param requestPath 去除 contextPath 后的请求路径
     * @return 方法和路径均一致时返回 true
     */
    public boolean matches(String requestMethod, String requestPath) {
        return method.equals(requestMethod) && path.equals(requestPath);
    }

}
