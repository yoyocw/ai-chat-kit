package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1AppPolicy;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1EndpointPolicy;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1Properties;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Policy;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 验证并复制部署策略，调用请求只能匹配已有策略，不提供新增、覆盖和通配授权。 */
final class AiMcpV1PolicyRegistry {
    /** 签发者固定信任值。 */
    private final String issuer;
    /** 目标资源固定受众。 */
    private final String audience;
    /** 仅 iat 使用的秒级容差。 */
    private final int skew;
    /** 不向外暴露的应用策略副本，按唯一 appId 索引。 */
    private final Map<String, AiMcpV1AppPolicy> apps = new HashMap<>();
    /** 不向外暴露的资源策略副本，按唯一 endpointCode 索引。 */
    private final Map<String, AiMcpV1EndpointPolicy> endpoints = new HashMap<>();

    /** 启用哪一端就校验哪一端的必需策略，避免资源端依赖签发端配置。 */
    AiMcpV1PolicyRegistry(AiMcpV1Properties properties, boolean signing) {
        if (signing) { copyApps(properties.getApps()); }
        else { copyEndpoints(properties.getEndpoints()); }
        boolean needsJwt = !signing || apps.values().stream().anyMatch(app -> !app.getEndpointCodes().isEmpty());
        issuer = needsJwt ? AiMcpV1Checks.text(properties.getIssuer()) : null;
        audience = needsJwt ? AiMcpV1Checks.text(properties.getAudience()) : null;
        skew = properties.getClockSkewSeconds();
        if (skew < 0 || skew > 30) { throw AiMcpV1Checks.denied(); }
    }

    /** 同一可信公钥同时用于签发自检，故内部 codec 始终严格启用验签；外部开关由装配控制。 */
    McpJwtV1Policy codecPolicy() { return new McpJwtV1Policy(issuer, audience, skew, true); }
    /** @return 固定签发者 */
    String issuer() { return issuer; }
    /** @return 固定受众 */
    String audience() { return audience; }

    /** 工具绑定必须与配置完全一致；不因客户端省略工具而签出另一套更宽接口权限。 */
    AiMcpV1AppPolicy app(AiInvocationAuthorizationRequest request) {
        AiMcpV1AppPolicy policy = apps.get(request.getAppId());
        if (policy == null || !Objects.equals(emptyToNull(policy.getMcpId()), emptyToNull(request.getMcpId()))
                || !new HashSet<>(policy.getToolIds()).equals(strings(request.getToolIds()))) {
            throw AiMcpV1Checks.denied();
        }
        return policy;
    }

    /** 接口码由资源路由选择，不存在时拒绝，不根据 JWT 动态注册。 */
    AiMcpV1EndpointPolicy endpoint(String code) {
        AiMcpV1EndpointPolicy policy = endpoints.get(code);
        if (policy == null) { throw AiMcpV1Checks.denied(); }
        return policy;
    }

    /** 将可变绑定配置复制成模块内部快照，并拒绝重复、空策略及矛盾的无工具配置。 */
    private void copyApps(List<AiMcpV1AppPolicy> source) {
        if (source == null || source.isEmpty() || source.size() > 1000) { throw AiMcpV1Checks.denied(); }
        for (AiMcpV1AppPolicy item : source) {
            if (item == null) { throw AiMcpV1Checks.denied(); }
            AiMcpV1AppPolicy copy = new AiMcpV1AppPolicy();
            copy.setAppId(AiMcpV1Checks.text(item.getAppId()));
            copy.setPermission(AiMcpV1Checks.text(item.getPermission()));
            copy.setMcpId(emptyToNull(item.getMcpId()));
            copy.setToolIds(new ArrayList<>(strings(item.getToolIds())));
            copy.setEndpointCodes(new ArrayList<>(strings(item.getEndpointCodes())));
            copy.setMaxPageSize(pageLimit(item.getMaxPageSize()));
            boolean hasTools = copy.getMcpId() != null || !copy.getToolIds().isEmpty();
            if (hasTools == copy.getEndpointCodes().isEmpty() || apps.put(copy.getAppId(), copy) != null) {
                throw AiMcpV1Checks.denied();
            }
        }
    }

    /** 资源端权限和分页约束完全来自部署配置。 */
    private void copyEndpoints(List<AiMcpV1EndpointPolicy> source) {
        if (source == null || source.isEmpty() || source.size() > 1000) { throw AiMcpV1Checks.denied(); }
        for (AiMcpV1EndpointPolicy item : source) {
            if (item == null) { throw AiMcpV1Checks.denied(); }
            AiMcpV1EndpointPolicy copy = new AiMcpV1EndpointPolicy();
            copy.setEndpointCode(AiMcpV1Checks.text(item.getEndpointCode()));
            copy.setPermission(AiMcpV1Checks.text(item.getPermission()));
            copy.setPageable(item.getPageable());
            copy.setMaxPageSize(pageLimit(item.getMaxPageSize()));
            if (endpoints.put(copy.getEndpointCode(), copy) != null) { throw AiMcpV1Checks.denied(); }
        }
    }

    /** 无默认空值、无重复和通配符；缺省配置对象的空 List 可以表示明确的无工具应用。 */
    private static Set<String> strings(List<String> source) {
        if (source == null || source.size() > 1000) { throw AiMcpV1Checks.denied(); }
        Set<String> result = new HashSet<>();
        for (String value : source) {
            if ("*".equals(AiMcpV1Checks.text(value)) || !result.add(value)) { throw AiMcpV1Checks.denied(); }
        }
        return result;
    }

    /** pageSize 上限始终与 v1 协议 1 到 200 一致。 */
    private static int pageLimit(int value) {
        if (value < 1 || value > 200) { throw AiMcpV1Checks.denied(); }
        return value;
    }

    /** 仅 null 和空串等价，空白或通配服务标识拒绝。 */
    private static String emptyToNull(String value) {
        if (value == null || value.isEmpty()) { return null; }
        if ("*".equals(AiMcpV1Checks.text(value))) { throw AiMcpV1Checks.denied(); }
        return value;
    }
}
