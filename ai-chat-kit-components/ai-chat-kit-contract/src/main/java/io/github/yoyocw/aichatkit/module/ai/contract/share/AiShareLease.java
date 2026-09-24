package io.github.yoyocw.aichatkit.module.ai.contract.share;

/** 分享专用不可变响应，不包含用户租户身份、模型会话、工具凭据或内部运行信息。 */
public final class AiShareLease {
    /** 随机不可预测分享码，不写日志。 */
    private final String shareCode;
    /** 存储实现按既有可信时钟确定并转换的UTC Unix毫秒失效时刻。 */
    private final long expireTimeMillis;
    /** 复制已校验分享结果；禁止将完整结果写入日志。 */
    public AiShareLease(String shareCode, long expireTimeMillis) {
        this.shareCode = shareCode;
        this.expireTimeMillis = expireTimeMillis;
    }
    /** @return 随机不可预测分享码，不写日志 */
    public String getShareCode() { return shareCode; }
    /** @return 存储实现确定的UTC Unix毫秒失效时刻 */
    public long getExpireTimeMillis() { return expireTimeMillis; }
}
