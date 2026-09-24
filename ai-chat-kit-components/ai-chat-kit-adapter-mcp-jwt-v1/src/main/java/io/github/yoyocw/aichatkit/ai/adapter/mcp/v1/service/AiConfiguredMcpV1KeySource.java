package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service;

import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1KeySource;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api.AiMcpV1SigningKey;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config.AiMcpV1KeyProperties;

/** 默认显式配置密钥源；固定 issuer/audience，构造时快照，不生成、不访问数据库、不回退。 */
public final class AiConfiguredMcpV1KeySource implements AiMcpV1KeySource {
    /** 固定签发者。 */
    private final String issuer;
    /** 固定资源受众。 */
    private final String audience;
    /** 仅内存保存的显式密钥快照，不提供 Bean getter。 */
    private final transient AiMcpV1SigningKey key;

    /** @param issuer 固定签发者 @param audience 固定受众 @param properties 显式秘密配置 */
    public AiConfiguredMcpV1KeySource(String issuer, String audience, AiMcpV1KeyProperties properties) {
        this.issuer = AiMcpV1Checks.text(issuer);
        this.audience = AiMcpV1Checks.text(audience);
        this.key = new AiMcpV1SigningKey(properties.privateKey(), properties.publicKey());
    }

    /** 固定信任范围内返回密钥快照，缺少私钥时由签发端启动校验拒绝。 */
    @Override
    public AiMcpV1SigningKey signingKey(String expectedIssuer, String expectedAudience) {
        matches(expectedIssuer, expectedAudience);
        return key;
    }

    /** 资源端只读取公钥，独立启用时不检查或要求私钥。 */
    @Override
    public String verificationKey(String expectedIssuer, String expectedAudience) {
        matches(expectedIssuer, expectedAudience);
        return key.publicKey();
    }

    /** 不允许跨签发者或跨受众复用固定信任材料。 */
    private void matches(String expectedIssuer, String expectedAudience) {
        if (!issuer.equals(expectedIssuer) || !audience.equals(expectedAudience)) { throw AiMcpV1Checks.denied(); }
    }
}
