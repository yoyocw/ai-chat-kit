package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SigningKey;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SubjectPort;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1AppPolicy;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1Properties;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Codec;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationResult;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Supplier;

/** 签发当前真实用户的受限 v1 工具 JWT；每次调用重新核验，不缓存、续期旧 JWT 或降级固定密钥。 */
public final class AiMcpV1AuthorizationService implements AiInvocationAuthorizationPort {
    /** 唯一部署命名空间，复用 starter.namespace。 */
    private final String namespace;
    /** 真实宿主登录上下文。 */
    private final AiInvocationContextPort contexts;
    /** 真实会话状态源。 */
    private final AiHostSessionPort sessions;
    /** 真实操作权限源。 */
    private final AiHostPermissionPort permissions;
    /** 实际工具调用时才获取唯一数值映射；普通聊天不依赖 v1 身份。 */
    private final Supplier<AiMcpV1SubjectPort> subjects;
    /** 实际工具调用时才获取唯一可信密钥源；失败拒绝工具授权。 */
    private final Supplier<AiMcpV1KeySource> keys;
    /** 不可由请求修改的部署策略快照。 */
    private final AiMcpV1PolicyRegistry policies;
    /** 纯协议编解码，不负责宿主授权。 */
    private final McpJwtV1Codec codec;
    /** 只有签发开关显式开启时才允许进入工具凭据路径。 */
    private final boolean signingEnabled;

    /**
     * @param namespace 固定 starter.namespace
     * @param properties 已启用的签发策略
     * @param contexts 宿主已认证上下文 @param sessions 真实会话源
     * @param permissions 真实权限源 @param subjects 唯一数值映射延迟获取器 @param keys 唯一密钥源延迟获取器
     * @throws IllegalStateException 配置无效；工具专用依赖及密钥在实际调用时校验
     */
    public AiMcpV1AuthorizationService(String namespace, AiMcpV1Properties properties,
            AiInvocationContextPort contexts, AiHostSessionPort sessions, AiHostPermissionPort permissions,
            Supplier<AiMcpV1SubjectPort> subjects, Supplier<AiMcpV1KeySource> keys) {
        this.namespace = AiMcpV1Checks.text(namespace);
        this.contexts = Objects.requireNonNull(contexts);
        this.sessions = Objects.requireNonNull(sessions);
        this.permissions = Objects.requireNonNull(permissions);
        this.subjects = Objects.requireNonNull(subjects);
        this.keys = Objects.requireNonNull(keys);
        this.signingEnabled = properties != null && properties.getSigningEnabled();
        this.policies = new AiMcpV1PolicyRegistry(properties, true);
        this.codec = policies.issuer() == null ? null : new McpJwtV1Codec(policies.codecPolicy());
    }

    /** 校验请求归属、固定应用绑定、真实会话和权限；返回完整 Bearer 头，仅供工具鉴权通道。 */
    @Override
    public AiInvocationAuthorizationResult authorize(AiInvocationAuthorizationRequest request) {
        try {
            AiMcpV1AppPolicy policy = policies.app(Objects.requireNonNull(request));
            AiInvocationContext context = capture(request);
            AiHostSession original = sessions.currentSession(context);
            AiMcpV1Checks.matches(namespace, context, original);
            AiHostSession current = sessions.checkSession(context, original.getSessionId());
            AiMcpV1Checks.sameSession(original, current);
            if (!permissions.hasPermission(context, policy.getPermission())) { throw AiMcpV1Checks.denied(); }
            long expiry = Math.min(AiMcpV1Checks.expiry(original), AiMcpV1Checks.expiry(current));
            if (policy.getEndpointCodes().isEmpty()) { return new AiInvocationAuthorizationResult(null); }
            // 普通应用授权不能凭借完整密钥/主体 SPI 绕过 signing-enabled=false。
            if (!signingEnabled) { throw AiMcpV1Checks.denied(); }
            McpJwtV1Identity identity = map(context, original, current);
            return sign(identity, policy, expiry);
        } catch (RuntimeException exception) { throw AiMcpV1Checks.denied(); }
    }

    /** 工具专用数值映射必须双向对应同一真实会话，不能把 opaque ID 强制转成数字。 */
    private McpJwtV1Identity map(AiInvocationContext context, AiHostSession original, AiHostSession current) {
        AiMcpV1SubjectPort source = Objects.requireNonNull(subjects.get());
        McpJwtV1Identity identity = AiMcpV1Checks.subject(source.mapSession(context, original));
        AiMcpV1Checks.sameSession(original, source.resolveSession(AiMcpV1Checks.subject(identity)));
        // 映射源可能耗时或会话已经变化，映射后重新检查原会话并限制签发期限。
        AiHostSession rechecked = sessions.checkSession(context, original.getSessionId());
        AiMcpV1Checks.sameSession(current, rechecked);
        if (AiMcpV1Checks.expiry(rechecked) < AiMcpV1Checks.expiry(current)) { throw AiMcpV1Checks.denied(); }
        AiMcpV1Checks.sameSubject(identity, source.mapSession(context, rechecked));
        return identity;
    }

    /** 捕获真实登录身份；外部 actor/tenant/namespace 仅用来比对，不能作为认证来源。 */
    private AiInvocationContext capture(AiInvocationAuthorizationRequest request) {
        AiInvocationContext context = contexts.capture(AiMcpV1Checks.text(request.getActorId()));
        if (context == null || !namespace.equals(request.getNamespace())
                || !namespace.equals(context.getNamespace())
                || !AiMcpV1Checks.text(request.getActorId()).equals(context.getActorId())
                || !AiMcpV1Checks.text(request.getTenantId()).equals(context.getTenantId())) {
            throw AiMcpV1Checks.denied();
        }
        AiMcpV1Checks.text(request.getInvocationId());
        AiMcpV1Checks.text(context.getInvocationId());
        return context;
    }

    /** 只签部署授予的接口/分页范围，公私钥取同一快照并自检，不传播 SPI 附带权限。 */
    private AiInvocationAuthorizationResult sign(McpJwtV1Identity identity, AiMcpV1AppPolicy policy, long expiry) {
        identity.setAllowedEndpointCodes(new HashSet<>(policy.getEndpointCodes()));
        identity.setMaxPageSize(policy.getMaxPageSize());
        AiMcpV1SigningKey key = Objects.requireNonNull(keys.get()).signingKey(policies.issuer(), policies.audience());
        AiMcpV1Keys.signing(key);
        String token = codec.sign(identity, expiry, key.privateKey());
        McpJwtV1Identity checked = codec.verify(token, key.publicKey());
        AiMcpV1Checks.sameSubject(identity, checked);
        if (!Long.valueOf(expiry).equals(checked.getExpiresAtEpochSecond())
                || expiry <= System.currentTimeMillis() / 1000) { throw AiMcpV1Checks.denied(); }
        return new AiInvocationAuthorizationResult("Bearer " + token);
    }
}
