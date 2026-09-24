package io.github.yoyocw.aichatkit.ai.adapter.web;

import javax.validation.constraints.*;

/** 对话HTTP输入；身份只能来自宿主认证上下文。 */
public final class AiWebShareRequest {
    /** 正数会话编号。 */
    @NotNull @Positive
    private Long id;
    /** @return 正数会话编号 */
    public Long getId() { return id; }
    /** @param id 正数会话编号 */
    public void setId(Long id) { this.id = id; }
    /** 分享有效天数；缺省7天。 */
    @Min(1) @Max(30)
    private Integer validDays = 7;
    /** @return 分享有效天数；缺省7天 */
    public Integer getValidDays() { return validDays; }
    /** @param validDays 分享有效天数；缺省7天 */
    public void setValidDays(Integer validDays) { this.validDays = validDays; }
}

