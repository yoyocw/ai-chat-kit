package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 已按归属过滤的不可变读取视图，不含宿主身份、模型会话或认证凭据。 */
public final class AiMessageView {
    /** AI自有消息编号。 */
    private final Long id;
    /** 所属会话编号。 */
    private final Long conversationId;
    /** user用户或assistant助手。 */
    private final String role;
    /** 消息正文，禁止日志输出。 */
    private final String content;
    /** 本轮地图展示意图。 */
    private final Boolean mapEnabled;
    /** 0生成中1完成2停止3失败。 */
    private final Integer status;
    /** 模型提供方请求编号，可为空。 */
    private final String requestId;
    /** 经过展示白名单处理的JSON，可为空。 */
    private final String responseData;
    /** 失败时安全文案，可为空。 */
    private final String errorMessage;
    /** 群聊发言者编码，单聊或用户消息可为空。 */
    private final String speakerCode;
    /** 群聊发言者名称，单聊或用户消息可为空。 */
    private final String speakerName;
    /** 群聊本轮发言顺序，从1开始，其他消息为空。 */
    private final Integer roundNo;
    /** 消息创建UTC Unix毫秒时刻。 */
    private final Long createTimeMillis;

    /** 原始字段是否来自宿主记录，真实空值不得用默认值补齐。 */
    private final boolean sourceValuesPresent;
    /** 宿主原始创建时间，保留精度及 SQL NULL。 */
    private final LocalDateTime sourceCreateTime;

    /** 复制已授权查询结果；正文对象不得整体写入日志。 */
    public AiMessageView(Long id, Long conversationId, String role, String content, boolean mapEnabled, int status, String requestId, String responseData, String errorMessage, String speakerCode, String speakerName, Integer roundNo, long createTimeMillis) {
        this(id, conversationId, role, content, mapEnabled, status, requestId, responseData, errorMessage,
                speakerCode, speakerName, roundNo, createTimeMillis, false, null);
    }

    /** 原构造保证 primitive 字段非空；源值构造保留可空列。 */
    private AiMessageView(Long id, Long conversationId, String role, String content, Boolean mapEnabled,
            Integer status, String requestId, String responseData, String errorMessage, String speakerCode,
            String speakerName, Integer roundNo, Long createTimeMillis, boolean sourceValuesPresent,
            LocalDateTime sourceCreateTime) {
        this.sourceValuesPresent = sourceValuesPresent;
        this.sourceCreateTime = sourceCreateTime;
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.mapEnabled = mapEnabled;
        this.status = status;
        this.requestId = requestId;
        this.responseData = responseData;
        this.errorMessage = errorMessage;
        this.speakerCode = speakerCode;
        this.speakerName = speakerName;
        this.roundNo = roundNo;
        this.createTimeMillis = createTimeMillis;
    }
    /** @return AI自有消息编号 */
    public Long getId() { return id; }
    /** @return 所属会话编号 */
    public Long getConversationId() { return conversationId; }
    /** @return user用户或assistant助手 */
    public String getRole() { return role; }
    /** @return 消息正文，禁止日志输出 */
    public String getContent() { return content; }
    /** @return 本轮地图展示意图 */
    public boolean isMapEnabled() { if (mapEnabled == null) { throw new IllegalStateException("源地图状态为空"); } return mapEnabled; }
    /** @return 0生成中1完成2停止3失败 */
    public int getStatus() { if (status == null) { throw new IllegalStateException("源消息状态为空"); } return status; }
    /** @return 模型提供方请求编号，可为空 */
    public String getRequestId() { return requestId; }
    /** @return 经过展示白名单处理的JSON，可为空 */
    public String getResponseData() { return responseData; }
    /** @return 失败时安全文案，可为空 */
    public String getErrorMessage() { return errorMessage; }
    /** @return 群聊发言者编码，单聊或用户消息可为空 */
    public String getSpeakerCode() { return speakerCode; }
    /** @return 群聊发言者名称，单聊或用户消息可为空 */
    public String getSpeakerName() { return speakerName; }
    /** @return 群聊本轮发言顺序，从1开始，其他消息为空 */
    public Integer getRoundNo() { return roundNo; }
    /** @return 消息创建UTC Unix毫秒时刻 */
    public long getCreateTimeMillis() { if (createTimeMillis == null) { throw new IllegalStateException("源创建时间为空"); } return createTimeMillis; }
    /** 从已授权宿主记录创建读取投影，不制造空状态、空地图值或空时间。 */
    public static AiMessageView fromSource(Long id, Long conversationId, String role, String content,
            Boolean mapEnabled, Integer status, String requestId, String responseData, String errorMessage,
            String speakerCode, String speakerName, Integer roundNo, LocalDateTime createTime) {
        Long millis = createTime == null ? null : createTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new AiMessageView(id, conversationId, role, content, mapEnabled, status, requestId, responseData,
                errorMessage, speakerCode, speakerName, roundNo, millis, true, createTime);
    }
    /** @return 是否携带真实宿主原始值 */
    public boolean hasSourceValues() { return sourceValuesPresent; }
    /** @return 原始可空创建时间 */
    public LocalDateTime getSourceCreateTime() { return sourceCreateTime; }
    /** @return 原始可空生成状态 */
    public Integer getSourceStatus() { return status; }
    /** @return 原始可空地图展示意图 */
    public Boolean getSourceMapEnabled() { return mapEnabled; }
}