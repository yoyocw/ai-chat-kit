package io.github.yoyocw.aichatkit.mcp.jwt;

/** 单次调用固定的旧协议信任策略；无林业默认值、密钥、端点配置或宿主框架依赖。 */
public final class McpJwtV1Policy {
    /** 必须精确匹配的签发者。 */
    private final String issuer;
    /** 必须精确匹配的接收方。 */
    private final String audience;
    /** 仅允许 iat 提前的秒数，0至30；不得扩展 exp。 */
    private final int clockSkewSeconds;
    /** 是否接受验签；false 连已签发凭据也拒绝，不控制固定密钥。 */
    private final boolean verificationEnabled;

    /**
     * @param issuer 固定签发者
     * @param audience 固定接收方
     * @param clockSkewSeconds iat 容忍偏差，秒
     * @param verificationEnabled 验签启停；有效性由编解码调用时检查
     */
    public McpJwtV1Policy(String issuer, String audience, int clockSkewSeconds, boolean verificationEnabled) {
        this.issuer = issuer;
        this.audience = audience;
        this.clockSkewSeconds = clockSkewSeconds;
        this.verificationEnabled = verificationEnabled;
    }

    /** @return 固定签发者 */
    public String getIssuer() { return issuer; }
    /** @return 固定接收方 */
    public String getAudience() { return audience; }
    /** @return 仅针对 iat 的时钟偏差秒数 */
    public int getClockSkewSeconds() { return clockSkewSeconds; }
    /** @return 当前是否接受 JWT 验签 */
    public boolean isVerificationEnabled() { return verificationEnabled; }
}
