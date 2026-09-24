package io.github.yoyocw.aichatkit.ai.adapter.web;

import javax.validation.constraints.*;

/** 对话HTTP输入；身份只能来自宿主认证上下文。 */
public final class AiWebGroupSendRequest {
    /** 已有群聊编号。 */
    @NotNull @Positive
    private Long conversationId;
    /** @return 已有群聊编号 */
    public Long getConversationId() { return conversationId; }
    /** @param conversationId 已有群聊编号 */
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    /** 本轮问题，禁止日志记录。 */
    @NotBlank @Size(max = 10000)
    private String content;
    /** @return 本轮问题，禁止日志记录 */
    public String getContent() { return content; }
    /** @param content 本轮问题，禁止日志记录 */
    public void setContent(String content) { this.content = content; }
}

