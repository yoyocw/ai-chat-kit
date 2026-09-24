package io.github.yoyocw.aichatkit.mcp.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * legacy login-user-v1 纯协议编解码器：只处理数值身份、RS256 和 UTC 秒期限。
 * 不认证登录会话，不计算业务权限，不支持 opaque 身份、kid 或其他协议版本。
 */
public final class McpJwtV1Codec {

    /** MCP 服务 JWT 前缀，使安全过滤器不会将其误当普通用户令牌。 */
    public static final String TOKEN_PREFIX = "mcp_jwt_";
    /** JWT 固定头部，只允许 RSA SHA-256 算法。 */
    private static final String HEADER_JSON = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    /** 防止异常超长请求消耗过多解码和验签资源。 */
    private static final int MAX_TOKEN_LENGTH = 8192;
    /** 用户权限协议版本；拒绝旧创建人快照 JWT，避免切换语义后意外扩大权限。 */
    private static final String PERMISSION_MODE = "login-user-v1";

    /** 单次调用使用的固定信任策略，不含私钥。 */
    private final McpJwtV1Policy policy;
    /** 初始化后只读的 JSON 编解码器；不注册宿主类型或记录待解码数据。 */
    private final ObjectMapper mapper;

    /**
     * @param policy 旧协议固定策略
     * @throws NullPointerException 策略缺失
     */
    public McpJwtV1Codec(McpJwtV1Policy policy) {
        this(policy, defaultMapper());
    }

    /**
     * 为旧包装保留宿主既有数字及空字段序列化规则；本类不修改传入的 Mapper。
     * @param policy 单次调用固定策略
     * @param mapper 已完成配置并可并发只读使用的 Mapper，不能在调用期间变更
     * @throws NullPointerException 参数缺失
     */
    public McpJwtV1Codec(McpJwtV1Policy policy, ObjectMapper mapper) {
        this.policy = Objects.requireNonNull(policy, "MCP JWT 策略不能为空");
        this.mapper = Objects.requireNonNull(mapper, "MCP JWT JSON 编解码器不能为空");
    }

    /**
     * 使用调用方提供的私钥快照签发；期限必须来自真实宿主会话，不能由终端用户指定。
     * @param identity login-user-v1 数值身份及端点范围；不透明身份不能转换或伪造编号
     * @param expiresAtEpochSecond 登录会话到期 UTC Unix 秒，不进行本地时区换算
     * @param privateKey PKCS#8 RSA 私钥，仅在本次调用内使用，不缓存不记录
     * @return 带 mcp_jwt_ 前缀的 RS256 JWT；结果仍须由资源服务复核真实会话
     * @throws IllegalArgumentException 身份、期限或长度无效
     * @throws IllegalStateException 配置、私钥或签名失败；无原始异常链
     */
    public String sign(McpJwtV1Identity identity, Long expiresAtEpochSecond, String privateKey) {
        validateIdentity(identity);
        validateConfiguration();
        // 到期时间仅来自可信登录上下文，不能通过外部请求指定或自行延长。
        if (expiresAtEpochSecond == null) {
            throw new IllegalArgumentException("MCP JWT 缺少登录令牌到期时间");
        }
        long expiresAt = expiresAtEpochSecond;
        long now = Instant.now().getEpochSecond();
        if (expiresAt <= now) {
            throw new IllegalArgumentException("MCP JWT 登录令牌已过期");
        }
        if (!hasText(privateKey)) {
            throw new IllegalStateException("MCP JWT 私钥未配置");
        }
        Map<String, Object> claims = new LinkedHashMap<String, Object>();
        claims.put("iss", policy.getIssuer());
        claims.put("aud", policy.getAudience());
        claims.put("iat", now);
        claims.put("exp", expiresAt);
        claims.put("permissionMode", PERMISSION_MODE);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("clientId", identity.getClientId());
        claims.put("clientCode", identity.getClientCode());
        claims.put("clientName", identity.getClientName());
        claims.put("serviceUserId", identity.getServiceUserId());
        claims.put("accessTokenId", identity.getAccessTokenId());
        claims.put("delegationClientId", identity.getDelegationClientId());
        claims.put("delegationBusinessSystem", identity.getDelegationBusinessSystem());
        claims.put("delegationEnvironment", identity.getDelegationEnvironment());
        claims.put("tenantId", identity.getTenantId());
        claims.put("maxPageSize", identity.getMaxPageSize());
        claims.put("nickname", identity.getNickname());
        claims.put("deptId", identity.getDeptId());
        claims.put("allowedEndpointCodes", identity.getAllowedEndpointCodes());
        String header = encode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payload = encode(encodeClaims(claims));
        String signingInput = header + "." + payload;
        String token = TOKEN_PREFIX + signingInput + "." + encode(sign(signingInput, parsePrivateKey(privateKey)));
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("MCP JWT 超出长度限制");
        }
        return token;
    }

    /**
     * 使用公钥快照离线校验旧协议 JWT；返回身份仍必须经过宿主真实会话及权限复核。
     *
     * @param token 带 MCP 前缀的服务 JWT
     * @param publicKey X.509 RSA 公钥快照，不能从未验签令牌中的地址或密钥字段获取
     * @return 仅通过密码学和协议边界校验的数值身份
     * @throws IllegalArgumentException JWT 格式、签名、时效或权限范围无效时抛出
     */
    public McpJwtV1Identity verify(String token, String publicKey) {
        try {
            // 与签发端启停分离：紧急撤销时连已签发的 JWT 也拒绝，不改变固定密钥认证。
            if (!policy.isVerificationEnabled()) {
                throw new IllegalArgumentException("MCP JWT 信任已停用");
            }
            validateConfiguration();
            if (!hasText(publicKey)) {
                throw new IllegalArgumentException("MCP JWT 公钥未配置");
            }
            if (token == null || token.length() > MAX_TOKEN_LENGTH || !token.startsWith(TOKEN_PREFIX)) {
                throw new IllegalArgumentException("MCP JWT 格式无效");
            }
            String compact = token.substring(TOKEN_PREFIX.length());
            String[] parts = compact.split("\\.", -1);
            if (parts.length != 3 || !HEADER_JSON.equals(new String(decode(parts[0]), StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("MCP JWT 头部无效");
            }
            String signingInput = parts[0] + "." + parts[1];
            if (!verify(signingInput, decode(parts[2]), parsePublicKey(publicKey))) {
                throw new IllegalArgumentException("MCP JWT 签名无效");
            }
            JsonNode claims = mapper.readTree(decode(parts[1]));
            validateClaims(claims);
            McpJwtV1Identity identity = mapper.treeToValue(
                    claims, McpJwtV1Identity.class);
            validateIdentity(identity);
            return identity;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("MCP JWT 无效");
        }
    }

    /** 校验协议版本和整数时间；时钟偏差仅容忍签发时间提前，绝不延长登录到期时间。 */
    private void validateClaims(JsonNode claims) {
        long now = Instant.now().getEpochSecond();
        long skew = policy.getClockSkewSeconds();
        if (!policy.getIssuer().equals(claims.path("iss").asText())
                || !policy.getAudience().equals(claims.path("aud").asText())
                || !PERMISSION_MODE.equals(claims.path("permissionMode").asText())
                || !claims.path("jti").isTextual() || !hasText(claims.path("jti").asText())
                || claims.path("jti").asText().length() > 64
                || !claims.path("iat").isIntegralNumber() || !claims.path("iat").canConvertToLong()
                || !claims.path("exp").isIntegralNumber() || !claims.path("exp").canConvertToLong()
                || claims.path("iat").asLong() <= 0 || claims.path("iat").asLong() > now + skew
                || claims.path("exp").asLong() <= now
                || claims.path("exp").asLong() <= claims.path("iat").asLong()) {
            throw new IllegalArgumentException("MCP JWT 声明无效");
        }
    }

    /** 校验可信用户、租户、只读端点和分页上限；数据权限由业务服务沿用系统规则计算。 */
    private void validateIdentity(McpJwtV1Identity identity) {
        if (identity == null || identity.getServiceUserId() == null || identity.getServiceUserId() <= 0
                || identity.getAccessTokenId() == null || identity.getAccessTokenId() <= 0
                || identity.getTenantId() == null || identity.getTenantId() < 0
                || identity.getMaxPageSize() == null || identity.getMaxPageSize() < 1
                || identity.getMaxPageSize() > 200 || identity.getAllowedEndpointCodes() == null
                || identity.getAllowedEndpointCodes().isEmpty()
                || identity.getAllowedEndpointCodes().stream().anyMatch(code -> !hasText(code))) {
            throw new IllegalArgumentException("MCP JWT 身份范围无效");
        }
    }

    /** 校验签发方、接收方和时钟偏差；JWT 有效期由登录令牌确定。 */
    private void validateConfiguration() {
        if (!hasText(policy.getIssuer()) || !hasText(policy.getAudience())
                || policy.getClockSkewSeconds() < 0 || policy.getClockSkewSeconds() > 30) {
            throw new IllegalStateException("MCP JWT 安全配置无效");
        }
    }

    /** 使用固定 RS256 算法对紧凑 JWT 的头部和载荷签名。 */
    private byte[] sign(String value, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(value.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("MCP JWT 签名失败");
        }
    }

    /** 使用固定 RS256 算法校验紧凑 JWT 签名。 */
    private boolean verify(String value, byte[] signatureValue, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(value.getBytes(StandardCharsets.US_ASCII));
        return signature.verify(signatureValue);
    }

    /** 解析 PKCS#8 RSA 私钥，支持标准 PEM 头尾和多行空白。 */
    private PrivateKey parsePrivateKey(String privateKey) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(parsePem(
                    privateKey, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----")));
        } catch (Exception ex) {
            throw new IllegalStateException("MCP JWT 私钥格式无效");
        }
    }

    /** 解析 X.509 RSA 公钥，支持标准 PEM 头尾和多行空白。 */
    private PublicKey parsePublicKey(String publicKey) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(parsePem(
                    publicKey, "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----")));
        } catch (Exception ex) {
            throw new IllegalArgumentException("MCP JWT 公钥格式无效");
        }
    }

    /** 去除 PEM 包装并使用严格 Base64 解码密钥材料。 */
    private byte[] parsePem(String value, String begin, String end) {
        String normalized = value.replace(begin, "").replace(end, "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    /** Base64URL 无填充编码，用于 JWT 三段紧凑格式。 */
    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** Base64URL 无填充解码，用于 JWT 三段紧凑格式。 */
    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("MCP JWT 格式无效");
        }
    }

    /** 纯协议默认 JSON 行为兼容旧 DTO 的未知字段和空字段语义。 */
    private static ObjectMapper defaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /** 序列化失败不得把身份载荷放入异常消息或 cause。 */
    private byte[] encodeClaims(Map<String, Object> claims) {
        try {
            return mapper.writeValueAsBytes(claims);
        } catch (Exception ex) {
            throw new IllegalStateException("MCP JWT 声明序列化失败");
        }
    }

    /** 与旧 Spring hasText 一致，识别 Character.isWhitespace 而不改写身份原值。 */
    private static boolean hasText(String value) {
        if (value == null || value.isEmpty()) { return false; }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) { return true; }
        }
        return false;
    }
}
