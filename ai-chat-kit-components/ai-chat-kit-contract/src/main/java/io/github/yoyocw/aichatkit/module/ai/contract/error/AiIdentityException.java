package io.github.yoyocw.aichatkit.module.ai.contract.error;

/** 仅携带安全分类的身份异常；兼容原 IllegalStateException，不接收原始消息或异常链。 */
public final class AiIdentityException extends IllegalStateException {
    /** 序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 宿主确认的失败类别；缺失类别按未知核验失败处理。 */
    private final AiIdentityError error;

    /** @param error 已确定的安全类别；null 不得被解释成未登录 */
    public AiIdentityException(AiIdentityError error) {
        super("宿主身份或授权核验未通过", null);
        this.error = error == null ? AiIdentityError.VERIFICATION_FAILED : error;
    }

    /** @return 不包含凭据、响应正文或宿主身份的稳定类别 */
    public AiIdentityError getError() { return error; }
}
