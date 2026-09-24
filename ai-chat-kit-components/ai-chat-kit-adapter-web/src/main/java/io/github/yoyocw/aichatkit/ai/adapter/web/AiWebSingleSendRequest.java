package io.github.yoyocw.aichatkit.ai.adapter.web;

import javax.validation.constraints.*;

/** 对话HTTP输入；身份只能来自宿主认证上下文。 */
public final class AiWebSingleSendRequest implements io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest {
    /** 已有会话编号；首次发送允许为空。 */
    @Positive
    private Long conversationId;
    /** @return 已有会话编号；首次发送允许为空 */
    public Long getConversationId() { return conversationId; }
    /** @param conversationId 已有会话编号；首次发送允许为空 */
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    /** 本轮问题，禁止日志记录。 */
    @NotBlank @Size(max = 10000)
    private String content;
    /** @return 本轮问题，禁止日志记录 */
    public String getContent() { return content; }
    /** @param content 本轮问题，禁止日志记录 */
    public void setContent(String content) { this.content = content; }
    /** 地图请求；缺省关闭。 */
    
    private Boolean mapEnabled = false;
    /** @return 地图请求；缺省关闭 */
    public Boolean getMapEnabled() { return mapEnabled; }
    /** @param mapEnabled 地图请求；缺省关闭 */
    public void setMapEnabled(Boolean mapEnabled) { this.mapEnabled = mapEnabled; }
}

