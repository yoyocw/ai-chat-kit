package io.github.yoyocw.aichatkit.module.ai.contract.error;

import java.util.Objects;

/** 执行入口校验失败；宿主将固定错误码映射为自己的业务异常响应。 */
public final class AiExecutionException extends RuntimeException {
    /** 跨宿主稳定错误分类。 */
    private final AiExecutionError error;

    /** @param error 非空固定错误分类 */
    public AiExecutionException(AiExecutionError error) {
        super(Objects.requireNonNull(error, "error").getMsg());
        this.error = error;
    }
    /** @return 可安全映射为 HTTP 或业务响应的错误分类 */
    public AiExecutionError getError() { return error; }
}
