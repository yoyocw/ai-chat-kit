package io.github.yoyocw.aichatkit.ai.adapter.web;

/** 仅由Web层的明确输入校验抛出；内部配置异常不能借此转成400。 */
public final class AiWebInputException extends IllegalArgumentException {
    /** 不携带原始输入，避免请求正文或认证信息进入响应。 */
    public AiWebInputException() { super("请求参数无效"); }
}
