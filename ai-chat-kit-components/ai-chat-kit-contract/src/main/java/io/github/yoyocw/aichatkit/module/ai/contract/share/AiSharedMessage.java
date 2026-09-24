package io.github.yoyocw.aichatkit.module.ai.contract.share;

import java.time.LocalDateTime;

/** 分享专用不可变响应，不包含用户租户身份、模型会话、工具凭据或内部运行信息。 */
public final class AiSharedMessage {
    /** user用户或assistant助手。 */
    private final String role;
    /** 当前已完成消息的正文，禁止日志输出。 */
    private final String content;
    /** 群聊发言者编码，单聊为空。 */
    private final String speakerCode;
    /** 群聊发言者名称，单聊为空。 */
    private final String speakerName;
    /** 群聊发言顺序从1开始，单聊为空。 */
    private final Integer roundNo;
    /** 消息创建UTC Unix毫秒时刻。 */
    private final long createTimeMillis;
    /** 可选原存储墙上时间，保留纳秒精度；不声明时区，仅供旧宿主响应还原。 */
    private final LocalDateTime sourceCreateTime;
    /** 复制已校验分享结果；禁止将完整结果写入日志。 */
    public AiSharedMessage(String role, String content, String speakerCode, String speakerName, Integer roundNo, long createTimeMillis) {
        this(role, content, speakerCode, speakerName, roundNo, createTimeMillis, null);
    }
    /** 从同一次存储读取复制时间；毫秒用于中立Web，原始时间用于旧宿主，不进行毫秒反构造。 */
    public AiSharedMessage(String role, String content, String speakerCode, String speakerName, Integer roundNo,
            long createTimeMillis, LocalDateTime sourceCreateTime) {
        this.role = role;
        this.content = content;
        this.speakerCode = speakerCode;
        this.speakerName = speakerName;
        this.roundNo = roundNo;
        this.createTimeMillis = createTimeMillis;
        this.sourceCreateTime = sourceCreateTime;
    }
    /** @return user用户或assistant助手 */
    public String getRole() { return role; }
    /** @return 当前已完成消息的正文，禁止日志输出 */
    public String getContent() { return content; }
    /** @return 群聊发言者编码，单聊为空 */
    public String getSpeakerCode() { return speakerCode; }
    /** @return 群聊发言者名称，单聊为空 */
    public String getSpeakerName() { return speakerName; }
    /** @return 群聊发言顺序从1开始，单聊为空 */
    public Integer getRoundNo() { return roundNo; }
    /** @return 消息创建UTC Unix毫秒时刻 */
    public long getCreateTimeMillis() { return createTimeMillis; }
    /** @return 原存储墙上时间；旧构造未提供时为null，不能据此推断时区 */
    public LocalDateTime getSourceCreateTime() { return sourceCreateTime; }
}
