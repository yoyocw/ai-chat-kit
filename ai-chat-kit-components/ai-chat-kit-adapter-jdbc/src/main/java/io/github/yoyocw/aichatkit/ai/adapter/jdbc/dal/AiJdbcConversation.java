package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

/** 会话行锁内读取的内部快照，不向客户端直接暴露。 */
public final class AiJdbcConversation {
    /** AI 自有会话主键。 */
    public final long id;
    /** 最近绑定的模型应用，可为空。 */
    public final String appId;
    /** 最近有效的模型会话，可为空。 */
    public final String sessionId;
    /** 本地滚动记忆，不记录到日志。 */
    public final String summary;
    /** 已归并进记忆的消息编号，可为空。 */
    public final Long cursor;

    /** 保存行锁内读取到的已有会话字段。 */
    public AiJdbcConversation(long id, String appId, String sessionId, String summary, Long cursor) {
        this.id = id; this.appId = appId; this.sessionId = sessionId; this.summary = summary; this.cursor = cursor;
    }
}
