package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 必须加入外层发送事务的准备命令，不包含模型凭据。 */
public final class AiSingleChatPrepareCommand {
    /** 已捕获且已授权的本轮上下文。 */
    private final AiInvocationContext context;
    /** 已有会话编号；null表示首次提问创建。 */
    private final Long conversationId;
    /** 本轮问题正文，沿用入口校验。 */
    private final String content;
    /** 是否请求地图展示。 */
    private final boolean mapEnabled;
    /** 本轮实际应用编号。 */
    private final String appId;
    /** 失联时长，单位秒；宿主有效读取超时加30秒，必须大于30。 */
    private final long staleTimeoutSeconds;
    /** 创建不可变的本轮快照。 */
    public AiSingleChatPrepareCommand(AiInvocationContext context, Long conversationId, String content, boolean mapEnabled, String appId, long staleTimeoutSeconds) {
        this.context = context;
        this.conversationId = conversationId;
        this.content = content;
        this.mapEnabled = mapEnabled;
        this.appId = appId;
        this.staleTimeoutSeconds = staleTimeoutSeconds;
    }
    public AiInvocationContext getContext() { return context; }
    public Long getConversationId() { return conversationId; }
    public String getContent() { return content; }
    public boolean isMapEnabled() { return mapEnabled; }
    public String getAppId() { return appId; }
    public long getStaleTimeoutSeconds() { return staleTimeoutSeconds; }
}
