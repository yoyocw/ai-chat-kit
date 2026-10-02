package io.github.yoyocw.aichatkit.module.ai.contract.model;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_CALL_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_TIMEOUT;

/** Safe failure without dynamic text or upstream causes. */
public class AiModelException extends IOException {
    private final AiExecutionError errorCode;
    private final boolean retryable;

    public AiModelException(AiExecutionError errorCode, boolean retryable) {
        super(Objects.requireNonNull(errorCode, "errorCode").getMsg());
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public AiExecutionError getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static AiModelException from(Throwable failure) {
        boolean timeout = false;
        for (int depth = 0; failure != null && depth < 32; depth++, failure = failure.getCause()) {
            if (failure instanceof AiModelException) {
                return (AiModelException) failure;
            }
            timeout |= failure instanceof InterruptedIOException;
        }
        return new AiModelException(timeout ? BAILIAN_TIMEOUT: BAILIAN_CALL_FAILED, true);
    }
}
