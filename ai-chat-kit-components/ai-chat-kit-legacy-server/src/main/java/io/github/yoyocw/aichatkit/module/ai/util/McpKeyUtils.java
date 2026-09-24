package io.github.yoyocw.aichatkit.module.ai.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * MCP 服务密钥工具类，用于生成高强度 Base64URL 密钥并计算后端配置所需的 SHA-256 摘要。
 */
public final class McpKeyUtils {

    /** MCP 服务密钥固定前缀，用于与普通 OAuth2 访问令牌区分。 */
    private static final String TOKEN_PREFIX = "mcp_";

    /** 随机载荷字节数；32 字节提供 256 位随机熵。 */
    private static final int RANDOM_BYTE_LENGTH = 32;

    /** 小写十六进制字符表，用于输出配置友好的 SHA-256 摘要。 */
    private static final char[] HEX_CHARACTERS = "0123456789abcdef".toCharArray();

    /** 密码学安全随机数生成器；SecureRandom 支持多线程安全调用。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成 MCP 服务原始密钥。
     *
     * @return 以 {@code mcp_} 开头、随机载荷为 32 字节 Base64URL 无填充编码的服务密钥
     */
    public static String generateToken() {
        byte[] randomBytes = new byte[RANDOM_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String encodedRandom = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return TOKEN_PREFIX + encodedRandom;
    }

    /**
     * 计算完整 MCP 服务密钥的 SHA-256 小写十六进制摘要。
     *
     * @param token 包含 {@code mcp_} 前缀的完整原始密钥，不能为空
     * @return 长度为 64 的小写十六进制 SHA-256 摘要
     * @throws IllegalArgumentException 原始密钥为空时抛出
     * @throws IllegalStateException 当前 Java 运行环境缺少标准 SHA-256 算法时抛出
     */
    public static String sha256Hex(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("MCP 服务密钥不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", ex);
        }
    }

    /**
     * 将字节数组转换为小写十六进制字符串。
     *
     * @param bytes SHA-256 摘要字节数组
     * @return 小写十六进制字符串
     */
    private static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            result[index * 2] = HEX_CHARACTERS[value >>> 4];
            result[index * 2 + 1] = HEX_CHARACTERS[value & 0x0F];
        }
        return new String(result);
    }

    private McpKeyUtils() {
    }
}
