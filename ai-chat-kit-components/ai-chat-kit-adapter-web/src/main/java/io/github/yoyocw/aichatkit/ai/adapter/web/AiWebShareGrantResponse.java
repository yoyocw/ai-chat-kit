package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareGrant;

/** HTTP 展示视图，时间使用Unix毫秒；不直接序列化引擎内部模型。 */
public final class AiWebShareGrantResponse {
    /** 分享码。 */
    private final String shareCode;
    /** 可信部署分享地址。 */
    private final String shareUrl;
    /** 失效Unix毫秒。 */
    private final long expireTime;
    /** @param value 已经完成授权的引擎视图 */
    public AiWebShareGrantResponse(AiShareGrant value) {
        this.shareCode = value.getShareCode();
        this.shareUrl = value.getShareUrl();
        this.expireTime = value.getExpireTimeMillis();
    }
    /** @return 分享码 */
    public String getShareCode() { return shareCode; }
    /** @return 可信部署分享地址 */
    public String getShareUrl() { return shareUrl; }
    /** @return 失效Unix毫秒 */
    public long getExpireTime() { return expireTime; }
}

