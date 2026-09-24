package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 默认密钥源的显式配置，仅启用适配器时绑定；不提供 Bean getter 或输出秘密的 toString。 */
@ConfigurationProperties(prefix = "ai-chat-kit.ai.mcp-jwt-v1.keys")
public class AiMcpV1KeyProperties {
    /** PKCS#8 RSA 私钥 PEM，仅签发端需要；建议通过宿主秘密配置注入。 */
    private transient String privateKey;
    /** X.509 RSA 公钥 PEM，签发自检和资源信任使用。 */
    private transient String publicKey;
    /** @param value 显式私钥，不自动生成或从其他来源回退 */
    public void setPrivateKey(String value) { privateKey = value; }
    /** @param value 显式可信公钥 */
    public void setPublicKey(String value) { publicKey = value; }
    /** @return 仅密钥源内部使用的私钥，禁止日志及响应输出 */
    public String privateKey() { return privateKey; }
    /** @return 固定公钥 */
    public String publicKey() { return publicKey; }
}
