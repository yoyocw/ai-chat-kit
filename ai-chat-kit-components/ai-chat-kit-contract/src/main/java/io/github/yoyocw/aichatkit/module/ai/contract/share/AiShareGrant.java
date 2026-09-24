package io.github.yoyocw.aichatkit.module.ai.contract.share;

/** 分享专用不可变响应，不包含用户租户身份、模型会话、工具凭据或内部运行信息。 */
public final class AiShareGrant {
    /** 32位小写hex分享码。 */
    private final String shareCode;
    /** 可信部署配置构建的公开访问地址。 */
    private final String shareUrl;
    /** UTC Unix毫秒失效时刻。 */
    private final long expireTimeMillis;
    /** 复制已校验分享结果；禁止将完整结果写入日志。 */
    public AiShareGrant(String shareCode, String shareUrl, long expireTimeMillis) {
        this.shareCode = shareCode;
        this.shareUrl = shareUrl;
        this.expireTimeMillis = expireTimeMillis;
    }
    /** @return 32位小写hex分享码 */
    public String getShareCode() { return shareCode; }
    /** @return 可信部署配置构建的公开访问地址 */
    public String getShareUrl() { return shareUrl; }
    /** @return UTC Unix毫秒失效时刻 */
    public long getExpireTimeMillis() { return expireTimeMillis; }
}
