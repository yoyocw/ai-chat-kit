package io.github.yoyocw.aichatkit.ai.adapter.web;

import javax.validation.constraints.*;

/** 对话HTTP输入；身份只能来自宿主认证上下文。 */
public final class AiWebMembersRequest {
    /** 正数会话编号。 */
    @NotNull @Positive
    private Long id;
    /** @return 正数会话编号 */
    public Long getId() { return id; }
    /** @param id 正数会话编号 */
    public void setId(Long id) { this.id = id; }
    /** 有序成员编码；不允许重复。 */
    @NotNull @Size(min = 2, max = 3)
    private java.util.List<@NotBlank @Pattern(regexp = "[A-Z0-9_-]{1,128}") String> memberCodes;
    /** @return 有序成员编码；不允许重复 */
    public java.util.List<String> getMemberCodes() { return memberCodes; }
    /** @param memberCodes 有序成员编码；不允许重复 */
    public void setMemberCodes(java.util.List<String> memberCodes) { this.memberCodes = memberCodes; }
}
