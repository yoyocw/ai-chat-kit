package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;

/** HTTP 展示视图，时间使用Unix毫秒；不直接序列化引擎内部模型。 */
public final class AiWebMessageResponse {
    /** 消息编号。 */
    private final Long id;
    /** 所属会话编号。 */
    private final Long conversationId;
    /** role。 */
    private final String role;
    /** content。 */
    private final String content;
    /** requestId。 */
    private final String requestId;
    /** 展示JSON字符串，保持历史协议。 */
    private final String responseData;
    /** errorMessage。 */
    private final String errorMessage;
    /** speakerCode。 */
    private final String speakerCode;
    /** speakerName。 */
    private final String speakerName;
    /** 地图展示意图。 */
    private final boolean mapEnabled;
    /** 0生成中1完成2停止3失败。 */
    private final int status;
    /** 群聊轮内顺序。 */
    private final Integer roundNo;
    /** 创建Unix毫秒。 */
    private final long createTime;
    /** @param value 已经完成授权的引擎视图 */
    public AiWebMessageResponse(AiMessageView value) {
        this.id = value.getId();
        this.conversationId = value.getConversationId();
        this.role = value.getRole();
        this.content = value.getContent();
        this.requestId = value.getRequestId();
        this.responseData = value.getResponseData();
        this.errorMessage = value.getErrorMessage();
        this.speakerCode = value.getSpeakerCode();
        this.speakerName = value.getSpeakerName();
        this.mapEnabled = value.isMapEnabled();
        this.status = value.getStatus();
        this.roundNo = value.getRoundNo();
        this.createTime = value.getCreateTimeMillis();
    }
    /** @return 消息编号 */
    public Long getId() { return id; }
    /** @return 所属会话编号 */
    public Long getConversationId() { return conversationId; }
    /** @return role */
    public String getRole() { return role; }
    /** @return content */
    public String getContent() { return content; }
    /** @return requestId */
    public String getRequestId() { return requestId; }
    /** @return 展示JSON字符串，保持历史协议 */
    public String getResponseData() { return responseData; }
    /** @return errorMessage */
    public String getErrorMessage() { return errorMessage; }
    /** @return speakerCode */
    public String getSpeakerCode() { return speakerCode; }
    /** @return speakerName */
    public String getSpeakerName() { return speakerName; }
    /** @return 地图展示意图 */
    public boolean getMapEnabled() { return mapEnabled; }
    /** @return 0生成中1完成2停止3失败 */
    public int getStatus() { return status; }
    /** @return 群聊轮内顺序 */
    public Integer getRoundNo() { return roundNo; }
    /** @return 创建Unix毫秒 */
    public long getCreateTime() { return createTime; }
}

