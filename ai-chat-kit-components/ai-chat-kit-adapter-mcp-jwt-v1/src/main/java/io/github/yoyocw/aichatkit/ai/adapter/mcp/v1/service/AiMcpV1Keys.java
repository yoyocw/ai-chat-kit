package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SigningKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** 启用时检查显式 RSA 密钥格式及配对，不创建用户 JWT，也不生成部署密钥。 */
final class AiMcpV1Keys {
    private AiMcpV1Keys() { }

    /** 校验资源端固定公钥，无需任何私钥。 */
    static void publicKey(String pem) {
        try { parsePublic(pem); }
        catch (Exception exception) { throw AiMcpV1Checks.denied(); }
    }

    /** 用内存随机挑战核对签名密钥对，不构造可用于访问业务的测试身份或 JWT。 */
    static void signing(AiMcpV1SigningKey key) {
        try {
            if (key == null) { throw AiMcpV1Checks.denied(); }
            PublicKey publicKey = parsePublic(key.publicKey());
            java.security.PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(decode(key.privateKey(), "PRIVATE")));
            strength(privateKey);
            byte[] challenge = new byte[32];
            new SecureRandom().nextBytes(challenge);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(challenge);
            byte[] proof = signature.sign();
            signature.initVerify(publicKey);
            signature.update(challenge);
            if (!signature.verify(proof)) { throw AiMcpV1Checks.denied(); }
        } catch (Exception exception) { throw AiMcpV1Checks.denied(); }
    }

    /** 仅 X.509 RSA 公钥作为信任材料，不接受由请求附带的 JWK/URL。 */
    private static PublicKey parsePublic(String pem) throws Exception {
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(decode(pem, "PUBLIC")));
        strength(key);
        return key;
    }

    /** 新适配器最低接受 2048 位 RSA，拒绝弱密钥；不改变旧 codec 的兼容行为。 */
    private static void strength(java.security.Key key) {
        if (!(key instanceof RSAKey) || ((RSAKey) key).getModulus().bitLength() < 2048) {
            throw AiMcpV1Checks.denied();
        }
    }

    /** 有界 PEM 解码，不回显输入或底层异常。 */
    private static byte[] decode(String pem, String kind) {
        AiMcpV1Checks.text(pem);
        if (pem.length() > 32768) { throw AiMcpV1Checks.denied(); }
        String begin = "-----BEGIN " + kind + " KEY-----";
        String end = "-----END " + kind + " KEY-----";
        String text = pem.trim();
        if (!text.startsWith(begin) || !text.endsWith(end)) { throw AiMcpV1Checks.denied(); }
        String encoded = text.substring(begin.length(), text.length() - end.length()).replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
    }
}
