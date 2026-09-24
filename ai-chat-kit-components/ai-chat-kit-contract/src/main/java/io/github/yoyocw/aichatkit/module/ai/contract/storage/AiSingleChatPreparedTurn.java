package io.github.yoyocw.aichatkit.module.ai.contract.storage;


/** 发送准备结果，不传递DO；仅在外层发送事务提交后才可异步执行。 */
public final class AiSingleChatPreparedTurn {
    /** 本轮本地会话编号。 */
    private final Long conversationId;
    /** 本轮助手生成占位消息编号。 */
    private final Long assistantMessageId;
    /** 应用绑定核对后可复用的远端会话；重置时为null。 */
    private final String remoteSessionId;
    /** 是否通知应用切换导致的上下文重建。 */
    private final boolean appChanged;
    /** 写入本轮用户消息前构建的历史上下文。 */
    private final String historySummary;
    /** 创建不可变的本轮快照。 */
    public AiSingleChatPreparedTurn(Long conversationId, Long assistantMessageId, String remoteSessionId, boolean appChanged, String historySummary) {
        this.conversationId = conversationId;
        this.assistantMessageId = assistantMessageId;
        this.remoteSessionId = remoteSessionId;
        this.appChanged = appChanged;
        this.historySummary = historySummary;
    }
    public Long getConversationId() { return conversationId; }
    public Long getAssistantMessageId() { return assistantMessageId; }
    public String getRemoteSessionId() { return remoteSessionId; }
    public boolean isAppChanged() { return appChanged; }
    public String getHistorySummary() { return historySummary; }
}
