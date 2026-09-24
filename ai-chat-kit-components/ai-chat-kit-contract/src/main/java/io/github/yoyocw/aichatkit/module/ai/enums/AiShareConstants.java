package io.github.yoyocw.aichatkit.module.ai.enums;

/**
 * AI 会话公开分享规则常量，统一单聊与群聊的状态、有效期和分享码冲突重试边界。
 */
public final class AiShareConstants {

    /** 分享已关闭或已撤销。 */
    public static final int STATUS_DISABLED = 0;
    /** 分享已启用且需同时满足未过期条件。 */
    public static final int STATUS_ACTIVE = 1;
    /** 未指定时采用的分享有效天数。 */
    public static final int DEFAULT_VALID_DAYS = 7;
    /** 允许设置的最短分享有效天数。 */
    public static final int MIN_VALID_DAYS = 1;
    /** 允许设置的最长分享有效天数。 */
    public static final int MAX_VALID_DAYS = 30;
    /** 随机分享码发生唯一键冲突时的最大生成次数。 */
    public static final int SHARE_CODE_GENERATION_ATTEMPTS = 3;

    private AiShareConstants() {
    }
}
