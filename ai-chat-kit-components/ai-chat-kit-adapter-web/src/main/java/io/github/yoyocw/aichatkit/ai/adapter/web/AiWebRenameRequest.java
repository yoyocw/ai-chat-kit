package io.github.yoyocw.aichatkit.ai.adapter.web;

import javax.validation.constraints.*;

/** 对话HTTP输入；身份只能来自宿主认证上下文。 */
public final class AiWebRenameRequest {
    /** 正数会话编号。 */
    @NotNull @Positive
    private Long id;
    /** @return 正数会话编号 */
    public Long getId() { return id; }
    /** @param id 正数会话编号 */
    public void setId(Long id) { this.id = id; }
    /** 新标题；群聊入口进一步限定30字符。 */
    @NotBlank @Size(max = 100)
    private String title;
    /** @return 新标题；群聊入口进一步限定30字符 */
    public String getTitle() { return title; }
    /** @param title 新标题；群聊入口进一步限定30字符 */
    public void setTitle(String title) { this.title = title; }
}

