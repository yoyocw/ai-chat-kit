package io.github.yoyocw.aichatkit.ai.adapter.web;

import javax.validation.constraints.*;

/** 对话HTTP输入；身份只能来自宿主认证上下文。 */
public final class AiWebGroupCreateRequest {
    /** 可选群聊标题；缺省或空白由真实成员生成默认标题，提供值最多30字符。 */
    @Size(max = 30)
    private String title;
    /** @return 群聊标题 */
    public String getTitle() { return title; }
    /** @param title 群聊标题 */
    public void setTitle(String title) { this.title = title; }
    /** 有序成员编码；不允许重复。 */
    @NotNull @Size(min = 2, max = 3)
    private java.util.List<@NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,128}") String> memberCodes;
    /** @return 有序成员编码；不允许重复 */
    public java.util.List<String> getMemberCodes() { return memberCodes; }
    /** @param memberCodes 有序成员编码；不允许重复 */
    public void setMemberCodes(java.util.List<String> memberCodes) { this.memberCodes = memberCodes; }
}
