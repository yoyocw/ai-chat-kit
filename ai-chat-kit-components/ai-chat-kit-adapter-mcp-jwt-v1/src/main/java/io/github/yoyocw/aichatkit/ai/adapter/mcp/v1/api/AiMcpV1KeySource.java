package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api;

/** 部署端显式信任源；一次调用返回同一 issuer/audience 的密钥，不接受请求指定公钥或自动回退。 */
public interface AiMcpV1KeySource {
    /**
     * @param issuer 固定可信签发者
     * @param audience 固定目标服务
     * @return 同一版本私钥和公钥快照，不自动生成；不得在日志或 JSON 中输出
     * @throws IllegalStateException 无匹配、停用、歧义或密钥不可用
     */
    AiMcpV1SigningKey signingKey(String issuer, String audience);

    /**
     * @param issuer 固定可信签发者
     * @param audience 固定目标服务
     * @return 显式信任的 X.509 RSA 公钥 PEM，资源端不需要私钥
     * @throws IllegalStateException 无可信公钥或密钥源不可用
     */
    String verificationKey(String issuer, String audience);
}
