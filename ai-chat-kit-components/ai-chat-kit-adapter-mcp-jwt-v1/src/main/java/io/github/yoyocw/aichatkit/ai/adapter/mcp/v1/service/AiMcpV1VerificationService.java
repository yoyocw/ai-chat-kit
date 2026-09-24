package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1VerifiedAccess;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1EndpointPolicy;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1Properties;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Codec;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 资源服务显式调用的用户 JWT 核验门面；不依赖当前登录线程或签发私钥。
 * 不注册 HTTP/filter，不认证机器调用方，不建立停止代理能力，不替代资源的数据行权限检查。
 */
public final class AiMcpV1VerificationService {
    /** 资源部署与映射源约定的固定命名空间。 */
    private final String namespace;
    /** 原真实会话状态源。 */
    private final AiHostSessionPort sessions;
    /** 当前真实用户权限源。 */
    private final AiHostPermissionPort permissions;
    /** 真实数值用户、租户和会话双向关联。 */
    private final AiMcpV1SubjectPort subjects;
    /** 资源服务固定公钥信任源。 */
    private final AiMcpV1KeySource keys;
    /** 固定受众与资源接口策略快照。 */
    private final AiMcpV1PolicyRegistry policies;
    /** 严格 v1 签名、用途和期限验证器。 */
    private final McpJwtV1Codec codec;

    /**
     * @param namespace 固定 starter.namespace @param properties 资源接口策略
     * @param sessions 真实会话核验 @param permissions 真实权限源
     * @param subjects 双向身份映射 @param keys 固定公钥源，不要求提供私钥
     * @throws IllegalStateException 配置或公钥无效，启用时拒绝装配
     */
    public AiMcpV1VerificationService(String namespace, AiMcpV1Properties properties,
            AiHostSessionPort sessions, AiHostPermissionPort permissions,
            AiMcpV1SubjectPort subjects, AiMcpV1KeySource keys) {
        this.namespace = AiMcpV1Checks.text(namespace);
        this.sessions = Objects.requireNonNull(sessions);
        this.permissions = Objects.requireNonNull(permissions);
        this.subjects = Objects.requireNonNull(subjects);
        this.keys = Objects.requireNonNull(keys);
        this.policies = new AiMcpV1PolicyRegistry(properties, false);
        this.codec = new McpJwtV1Codec(policies.codecPolicy());
        try { AiMcpV1Keys.publicKey(keys.verificationKey(policies.issuer(), policies.audience())); }
        catch (RuntimeException exception) { throw AiMcpV1Checks.denied(); }
    }

    /**
     * @param token 从 Authorization 中提取的裸 mcp_jwt_ 令牌，不接受固定密钥或公钥输入
     * @param serverEndpointCode 资源路由固定接口码，不得直接采用请求自报值
     * @param pageSize 本次实际分页条数；分页接口必填，非分页接口必须为空
     * @return 经真实源复核的用户会话和本次接口范围，宿主随后执行数据权限过滤
     * @throws IllegalStateException 签名、真实会话、权限、接口或分页约束不符；无降级重试
     */
    public AiMcpV1VerifiedAccess verify(String token, String serverEndpointCode, Integer pageSize) {
        try {
            AiMcpV1EndpointPolicy policy = policies.endpoint(serverEndpointCode);
            String publicKey = keys.verificationKey(policies.issuer(), policies.audience());
            AiMcpV1Keys.publicKey(publicKey);
            McpJwtV1Identity proof = codec.verify(token, publicKey);
            // 在调用可变 SPI 前保留证明快照，回调不得改写 exp、授权范围或身份来扩大权限。
            McpJwtV1Identity expected = AiMcpV1Checks.subject(proof);
            long expiry = Objects.requireNonNull(proof.getExpiresAtEpochSecond());
            Set<String> endpoints = new HashSet<>(proof.getAllowedEndpointCodes());
            int maximum = Math.min(policy.getMaxPageSize(), proof.getMaxPageSize());
            if (!endpoints.contains(serverEndpointCode)) { throw AiMcpV1Checks.denied(); }
            page(policy, pageSize, maximum);
            AiHostSession original = subjects.resolveSession(AiMcpV1Checks.subject(expected));
            AiInvocationContext context = context(original);
            AiHostSession current = sessions.checkSession(context, original.getSessionId());
            AiMcpV1Checks.sameSession(original, current);
            AiMcpV1Checks.sameSubject(expected, subjects.mapSession(context, current));
            if (!permissions.hasPermission(context, policy.getPermission())) { throw AiMcpV1Checks.denied(); }
            // 权限源可能耗时；再次核对原关联及原会话，不能用同用户的新会话替代已注销会话。
            AiMcpV1Checks.sameSubject(expected, subjects.mapSession(context, current));
            AiHostSession confirmed = sessions.checkSession(context, original.getSessionId());
            AiMcpV1Checks.sameSession(original, confirmed);
            if (expiry <= System.currentTimeMillis() / 1000
                    || expiry > Math.min(Math.min(AiMcpV1Checks.expiry(original), AiMcpV1Checks.expiry(current)),
                            AiMcpV1Checks.expiry(confirmed))) {
                throw AiMcpV1Checks.denied();
            }
            AiHostSession bounded = new AiHostSession(namespace, confirmed.getTenantId(), confirmed.getActorId(),
                    confirmed.getSessionId(), expiry * 1000);
            return new AiMcpV1VerifiedAccess(bounded, serverEndpointCode, maximum);
        } catch (RuntimeException exception) { throw AiMcpV1Checks.denied(); }
    }

    /** 仅在固定公钥验签成功后恢复宿主关联；随后必须以 checkSession 复核，不能伪装为当前登录。 */
    private AiInvocationContext context(AiHostSession session) {
        AiMcpV1Checks.expiry(session);
        if (!namespace.equals(session.getNamespace())) { throw AiMcpV1Checks.denied(); }
        AiInvocationContext context = new AiInvocationContext(namespace, session.getTenantId(),
                session.getActorId(), UUID.randomUUID().toString());
        AiMcpV1Checks.matches(namespace, context, session);
        return context;
    }

    /** 非分页接口拒绝分页参数，分页接口强制有效条数，资源处理器必须使用同一个已核验值。 */
    private void page(AiMcpV1EndpointPolicy policy, Integer size, int maximum) {
        if (policy.getPageable() ? size == null || size < 1 || size > maximum : size != null) {
            throw AiMcpV1Checks.denied();
        }
    }
}
