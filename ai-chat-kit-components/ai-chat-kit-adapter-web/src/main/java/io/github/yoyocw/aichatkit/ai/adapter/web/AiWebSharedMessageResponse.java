package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedMessage;

/** HTTP 展示视图，时间使用Unix毫秒；不直接序列化引擎内部模型。 */
public final class AiWebSharedMessageResponse {
    /** role。 */
    private final String role;
    /** content。 */
    private final String content;
    /** speakerCode。 */
    private final String speakerCode;
    /** speakerName。 */
    private final String speakerName;
    /** 群聊轮内顺序。 */
    private final Integer roundNo;
    /** 创建Unix毫秒。 */
    private final long createTime;
    /** @param value 已经完成授权的引擎视图 */
    public AiWebSharedMessageResponse(AiSharedMessage value) {
        this.role = value.getRole();
        this.content = value.getContent();
        this.speakerCode = value.getSpeakerCode();
        this.speakerName = value.getSpeakerName();
        this.roundNo = value.getRoundNo();
        this.createTime = value.getCreateTimeMillis();
    }
    /** @return role */
    public String getRole() { return role; }
    /** @return content */
    public String getContent() { return content; }
    /** @return speakerCode */
    public String getSpeakerCode() { return speakerCode; }
    /** @return speakerName */
    public String getSpeakerName() { return speakerName; }
    /** @return 群聊轮内顺序 */
    public Integer getRoundNo() { return roundNo; }
    /** @return 创建Unix毫秒 */
    public long getCreateTime() { return createTime; }
}

