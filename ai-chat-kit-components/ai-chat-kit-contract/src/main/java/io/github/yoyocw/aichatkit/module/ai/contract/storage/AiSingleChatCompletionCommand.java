package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 单聊完成命令；纯JDK与中立契约，不传递DO或模型SDK类型。 */
public final class AiSingleChatCompletionCommand {
    /** 同步捕获并由异步作用域恢复租户的本轮身份。 */
    private final AiInvocationContext context;
    /** 本地会话编号。 */
    private final Long conversationId;
    /** 本轮助手占位消息编号。 */
    private final Long messageId;
    /** 本轮实际调用应用编号。 */
    private final String appId;
    /** 完成回答正文，禁止记录到日志。 */
    private final String content;
    /** 模型提供方请求编号，可为空。 */
    private final String requestId;
    /** 可选结构化展示JSON，沿用已有序列化协议。 */
    private final String responseData;
    /** 可选远端会话编号，只按当前app与turn条件保存。 */
    private final String sessionId;
    /** 创建当前调用的完成快照；不得向HTTP暴露或写入日志。 */
    public AiSingleChatCompletionCommand(AiInvocationContext context, Long conversationId, Long messageId, String appId, String content, String requestId, String responseData, String sessionId) {
        this.context = context;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.appId = appId;
        this.content = content;
        this.requestId = requestId;
        this.responseData = responseData;
        this.sessionId = sessionId;
    }
    public AiInvocationContext getContext() { return context; }
    public Long getConversationId() { return conversationId; }
    public Long getMessageId() { return messageId; }
    public String getAppId() { return appId; }
    public String getContent() { return content; }
    public String getRequestId() { return requestId; }
    public String getResponseData() { return responseData; }
    public String getSessionId() { return sessionId; }
}
