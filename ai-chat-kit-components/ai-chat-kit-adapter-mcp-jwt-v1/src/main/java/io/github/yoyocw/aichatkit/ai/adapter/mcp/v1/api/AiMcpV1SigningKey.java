package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api;

/** 单一版本签发密钥快照；无 Bean getter/toString，避免普通对象序列化泄露秘密。 */
public final class AiMcpV1SigningKey {
    /** PKCS#8 RSA 私钥 PEM，仅签发端内存使用。 */
    private final transient String privateKey;
    /** 与私钥配对的 X.509 RSA 公钥 PEM，用于签发自检。 */
    private final transient String publicKey;

    /** @param privateKey 显式配置私钥 @param publicKey 同版本公钥；格式和配对由使用方检查 */
    public AiMcpV1SigningKey(String privateKey, String publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }
    /** @return 仅供签发器使用的私钥，禁止传播到日志和响应 */
    public String privateKey() { return privateKey; }
    /** @return 本次快照的配对公钥 */
    public String publicKey() { return publicKey; }
}
