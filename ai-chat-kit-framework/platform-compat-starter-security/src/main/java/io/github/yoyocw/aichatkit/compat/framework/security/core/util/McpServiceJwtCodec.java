package io.github.yoyocw.aichatkit.compat.framework.security.core.util;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import io.github.yoyocw.aichatkit.compat.framework.security.config.McpServiceJwtProperties;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Codec;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Policy;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;

/**
 * 既有 MCP JWT API 的兼容包装，协议算法集中在独立 ai-chat-kit-mcp-jwt 模块。
 * 保留数值身份 DTO、本地时间参数、配置键及公开方法；不改变过滤器的真实会话复核。
 */
public class McpServiceJwtCodec {
    /** 原前缀保持不变，失败不得降级为固定 mcp_ 密钥。 */
    public static final String TOKEN_PREFIX = McpJwtV1Codec.TOKEN_PREFIX;
    /** 宿主兼容配置；密钥不向纯策略对象传播或缓存。 */
    private final McpServiceJwtProperties properties;

    /** @param properties 既有 aichatkit.security.mcp-jwt 配置，不改变宿主装配方式 */
    public McpServiceJwtCodec(McpServiceJwtProperties properties) {
        this.properties = properties;
    }

    /**
     * @param identity 既有数值身份及只读端点范围
     * @param loginExpiresTime 真实登录令牌到期时间，保持宿主原本地时间语义
     * @return 带原前缀的 RS256 凭据
     * @throws IllegalArgumentException 身份或期限无效
     * @throws IllegalStateException 配置或私钥不可用
     */
    public String sign(ServiceApiKeyAuthRespDTO identity, LocalDateTime loginExpiresTime) {
        return sign(identity, loginExpiresTime, properties.getPrivateKey());
    }

    /**
     * @param identity 真实用户的旧协议数值身份，不映射不透明宿主编号
     * @param loginExpiresTime 登录令牌真实到期时间；时区转换仅留在兼容层
     * @param privateKey 调用方读取的 PKCS#8 私钥快照，不缓存不记录
     * @return 与旧协议互通的签名凭据
     * @throws IllegalArgumentException 身份、期限或长度无效
     * @throws IllegalStateException 配置或签名失败，错误不附带敏感 cause
     */
    public String sign(ServiceApiKeyAuthRespDTO identity, LocalDateTime loginExpiresTime, String privateKey) {
        Long expiresAt = loginExpiresTime == null ? null : loginExpiresTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return codec().sign(toProtocol(identity), expiresAt, privateKey);
    }

    /**
     * 只完成密码学与旧协议边界检查；调用方继续原有动态会话及业务权限复核。
     * @param token 带 mcp_jwt_ 前缀的旧协议凭据
     * @return 原 DTO，包含 exp 到 expiresAtEpochSecond 的映射
     * @throws IllegalArgumentException 凭据、签名、期限或信任无效
     */
    public ServiceApiKeyAuthRespDTO verify(String token) {
        return toLegacy(codec().verify(token, properties.getPublicKey()));
    }

    /** @return 既有私钥配置是否有文本；不是密钥有效性或完整认证就绪证明 */
    public boolean canSign() {
        return StringUtils.hasText(properties.getPrivateKey());
    }

    /** 每次取得当前配置与既有 Mapper，保留宿主大整数及空字段的原序列化规则。 */
    private McpJwtV1Codec codec() {
        McpJwtV1Policy policy = new McpJwtV1Policy(properties.getIssuer(), properties.getAudience(),
                properties.getClockSkewSeconds(), properties.isVerificationEnabled());
        return new McpJwtV1Codec(policy, JsonUtils.getObjectMapper());
    }

    /** 显式复制原 DTO 全部字段，避免 JSON 中转改变 exp、数值或委托绑定语义。 */
    private McpJwtV1Identity toProtocol(ServiceApiKeyAuthRespDTO source) {
        if (source == null) { return null; }
        McpJwtV1Identity target = new McpJwtV1Identity();
        target.setClientId(source.getClientId());
        target.setClientCode(source.getClientCode());
        target.setClientName(source.getClientName());
        target.setServiceUserId(source.getServiceUserId());
        target.setTenantId(source.getTenantId());
        target.setMaxPageSize(source.getMaxPageSize());
        target.setNickname(source.getNickname());
        target.setDeptId(source.getDeptId());
        target.setExpiresAtEpochSecond(source.getExpiresAtEpochSecond());
        target.setAccessTokenId(source.getAccessTokenId());
        target.setDelegationClientId(source.getDelegationClientId());
        target.setDelegationBusinessSystem(source.getDelegationBusinessSystem());
        target.setDelegationEnvironment(source.getDelegationEnvironment());
        target.setAllowedCreatorIds(source.getAllowedCreatorIds() == null ? null : new HashSet<>(source.getAllowedCreatorIds()));
        target.setAllowedEndpointCodes(source.getAllowedEndpointCodes() == null ? null : new HashSet<>(source.getAllowedEndpointCodes()));
        return target;
    }

    /** 显式还原原 DTO，包括固定密钥兼容集合字段；用户 JWT 的数据权限规则仍由过滤器决定。 */
    private ServiceApiKeyAuthRespDTO toLegacy(McpJwtV1Identity source) {
        ServiceApiKeyAuthRespDTO target = new ServiceApiKeyAuthRespDTO();
        target.setClientId(source.getClientId());
        target.setClientCode(source.getClientCode());
        target.setClientName(source.getClientName());
        target.setServiceUserId(source.getServiceUserId());
        target.setTenantId(source.getTenantId());
        target.setMaxPageSize(source.getMaxPageSize());
        target.setNickname(source.getNickname());
        target.setDeptId(source.getDeptId());
        target.setExpiresAtEpochSecond(source.getExpiresAtEpochSecond());
        target.setAccessTokenId(source.getAccessTokenId());
        target.setDelegationClientId(source.getDelegationClientId());
        target.setDelegationBusinessSystem(source.getDelegationBusinessSystem());
        target.setDelegationEnvironment(source.getDelegationEnvironment());
        target.setAllowedCreatorIds(source.getAllowedCreatorIds() == null ? null : new HashSet<>(source.getAllowedCreatorIds()));
        target.setAllowedEndpointCodes(source.getAllowedEndpointCodes() == null ? null : new HashSet<>(source.getAllowedEndpointCodes()));
        return target;
    }
}
