package io.github.yoyocw.aichatkit.compat.framework.security.core.filter;

import io.github.yoyocw.aichatkit.compat.framework.security.core.annotation.McpServiceApi;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MCP 注解端点只读注册表。
 *
 * <p>应用启动时扫描 Controller 映射，严格校验端点编码、HTTP 方法和路径，运行期间不接受外部修改。</p>
 */
public class McpServiceApiEndpointRegistry {

    /** 稳定端点编码格式，至少包含两个以点分隔的业务段。 */
    private static final Pattern ENDPOINT_CODE_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){1,7}$");

    /** 按“HTTP 方法 + 空格 + 精确路径”索引的不可变端点集合。 */
    private final Map<String, ServiceApiKeyEndpoint> endpoints;

    /**
     * 扫描并校验当前微服务的 MCP Controller 方法。
     *
     * @param handlerMapping Spring MVC Controller 路由注册表
     * @throws IllegalStateException 注解端点存在写方法、通配路径或重复编码时抛出
     */
    public McpServiceApiEndpointRegistry(RequestMappingHandlerMapping handlerMapping) {
        this.endpoints = Collections.unmodifiableMap(scanEndpoints(handlerMapping));
    }

    /**
     * 按请求方法和精确路径查找注解端点。
     *
     * @param method 请求 HTTP 方法
     * @param path 去除 ContextPath 后的完整请求路径
     * @return 匹配的端点元数据，未声明时返回 null
     */
    public ServiceApiKeyEndpoint find(String method, String path) {
        return endpoints.get(buildRouteKey(method, path));
    }

    /** 扫描全部 HandlerMethod，并对安全约束执行失败关闭校验。 */
    private Map<String, ServiceApiKeyEndpoint> scanEndpoints(RequestMappingHandlerMapping handlerMapping) {
        Map<String, ServiceApiKeyEndpoint> result = new LinkedHashMap<>();
        Map<String, String> codeRoutes = new LinkedHashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            McpServiceApi annotation = entry.getValue().getMethodAnnotation(McpServiceApi.class);
            if (annotation == null) {
                continue;
            }
            String endpointCode = validateEndpointCode(annotation.endpointCode(), entry.getValue());
            validateGetOnly(entry.getKey(), entry.getValue());
            String path = resolveExactPath(entry.getKey(), entry.getValue());
            String routeKey = buildRouteKey(RequestMethod.GET.name(), path);
            validateUnique(codeRoutes, result, endpointCode, routeKey, entry.getValue());
            result.put(routeKey, new ServiceApiKeyEndpoint(endpointCode, RequestMethod.GET.name(), path,
                    annotation.pageable()));
            codeRoutes.put(endpointCode, routeKey);
        }
        return result;
    }

    /** 校验授权编码格式，避免路径、空白或临时文字成为数据库安全契约。 */
    private String validateEndpointCode(String endpointCode, HandlerMethod handlerMethod) {
        if (endpointCode == null || endpointCode.length() > 128
                || !ENDPOINT_CODE_PATTERN.matcher(endpointCode).matches()) {
            throw invalidEndpoint(handlerMethod, "endpointCode 格式无效");
        }
        return endpointCode;
    }

    /** 校验注解方法只能映射到唯一的 GET 请求方法。 */
    private void validateGetOnly(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
        if (methods.size() != 1 || !methods.contains(RequestMethod.GET)) {
            throw invalidEndpoint(handlerMethod, "只允许声明唯一 GET 方法");
        }
    }

    /** 兼容 AntPath 与 PathPattern 两种 Spring MVC 模式并提取唯一精确路径。 */
    private String resolveExactPath(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod) {
        Set<String> paths = new LinkedHashSet<>();
        if (mappingInfo.getPatternsCondition() != null) {
            paths.addAll(mappingInfo.getPatternsCondition().getPatterns());
        }
        if (mappingInfo.getPathPatternsCondition() != null) {
            for (PathPattern pathPattern : mappingInfo.getPathPatternsCondition().getPatterns()) {
                paths.add(pathPattern.getPatternString());
            }
        }
        if (paths.size() != 1) {
            throw invalidEndpoint(handlerMethod, "必须映射到唯一精确路径");
        }
        String path = paths.iterator().next();
        if (!path.startsWith("/") || path.indexOf('*') >= 0 || path.indexOf('?') >= 0
                || path.indexOf('{') >= 0 || path.indexOf('}') >= 0) {
            throw invalidEndpoint(handlerMethod, "路径不允许使用通配符或路径变量");
        }
        return path;
    }

    /** 校验本服务内端点编码和 HTTP 路由均不重复。 */
    private void validateUnique(Map<String, String> codeRoutes, Map<String, ServiceApiKeyEndpoint> endpoints,
                                String endpointCode, String routeKey, HandlerMethod handlerMethod) {
        if (codeRoutes.containsKey(endpointCode)) {
            throw invalidEndpoint(handlerMethod, "endpointCode 与 " + codeRoutes.get(endpointCode) + " 重复");
        }
        if (endpoints.containsKey(routeKey)) {
            throw invalidEndpoint(handlerMethod, "HTTP 路由重复");
        }
    }

    /** 构造不包含请求参数的精确路由索引键。 */
    private String buildRouteKey(String method, String path) {
        return method + " " + path;
    }

    /** 生成包含 Controller 方法定位信息的启动异常，但不输出任何凭据材料。 */
    private IllegalStateException invalidEndpoint(HandlerMethod handlerMethod, String reason) {
        return new IllegalStateException("MCP 端点声明无效: " + handlerMethod.getShortLogMessage() + ", " + reason);
    }
}
