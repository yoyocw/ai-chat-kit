package io.github.yoyocw.aichatkit.ai.adapter.web;

import javax.validation.constraints.*;

/** 对话HTTP输入；身份只能来自宿主认证上下文。 */
public final class AiWebPinRequest {
    /** 正数会话编号。 */
    @NotNull @Positive
    private Long id;
    /** @return 正数会话编号 */
    public Long getId() { return id; }
    /** @param id 正数会话编号 */
    public void setId(Long id) { this.id = id; }
    /** 是否置顶。 */
    @NotNull
    private Boolean pinned;
    /** @return 是否置顶 */
    public Boolean getPinned() { return pinned; }
    /** @param pinned 是否置顶 */
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
}

