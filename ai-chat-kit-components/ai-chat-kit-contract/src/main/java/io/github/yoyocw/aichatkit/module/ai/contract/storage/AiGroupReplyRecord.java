package io.github.yoyocw.aichatkit.module.ai.contract.storage;

/** 已解析的群聊成员回复，不引用模型SDK、Controller VO或数据库DO。 */
public final class AiGroupReplyRecord {
    /** 已核验发言者编码。 */
    private final String speakerCode;
    /** 本轮成员快照确定的显示名。 */
    private final String speakerName;
    /** 完整回复内容，不得输出至日志。 */
    private final String content;

    /** 构造回复快照；轮次由有序回复列表位置决定。 */
    public AiGroupReplyRecord(String speakerCode, String speakerName, String content) {
        this.speakerCode = speakerCode;
        this.speakerName = speakerName;
        this.content = content;
    }
    public String getSpeakerCode() { return speakerCode; }
    public String getSpeakerName() { return speakerName; }
    public String getContent() { return content; }
}
