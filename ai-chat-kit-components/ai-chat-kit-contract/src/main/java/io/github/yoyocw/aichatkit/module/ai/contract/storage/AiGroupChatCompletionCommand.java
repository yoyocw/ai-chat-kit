package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 群聊原子完成命令，保存占位、后续回复和远端会话时使用同一事务。 */
public final class AiGroupChatCompletionCommand {
    /** 已由宿主恢复的本轮身份。 */
    private final AiInvocationContext context;
    /** 本地会话编号。 */
    private final Long conversationId;
    /** 本轮生成占位消息编号。 */
    private final Long messageId;
    /** 实际模型应用编号。 */
    private final String appId;
    /** 提供方请求编号，可为空。 */
    private final String requestId;
    /** 可选远端会话编号。 */
    private final String sessionId;
    /** 已过白名单处理的可选展示JSON。 */
    private final String responseData;
    /** 按发言顺序排列的非空回复快照。 */
    private final List<AiGroupReplyRecord> replies;

    /** 构造本轮完成快照，复制列表，禁止记录整个命令。 */
    public AiGroupChatCompletionCommand(AiInvocationContext context, Long conversationId, Long messageId,
            String appId, String requestId, String sessionId, String responseData, List<AiGroupReplyRecord> replies) {
        this.context = context;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.appId = appId;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.responseData = responseData;
        this.replies = replies == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<AiGroupReplyRecord>(replies));
    }
    public AiInvocationContext getContext() { return context; }
    public Long getConversationId() { return conversationId; }
    public Long getMessageId() { return messageId; }
    public String getAppId() { return appId; }
    public String getRequestId() { return requestId; }
    public String getSessionId() { return sessionId; }
    public String getResponseData() { return responseData; }
    public List<AiGroupReplyRecord> getReplies() { return replies; }
}
