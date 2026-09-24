package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.Objects;

import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.*;

/** 百炼调用的安全业务异常；消息可直接展示或持久化，不保留上游原文与异常原因链。 */
public class BailianCallException extends IOException {

    /** AI 模块固定错误码与安全中文文案，不接受上游动态错误消息。 */
    private final AiExecutionError errorCode;
    /** 是否允许用户稍后手动重试；不触发后台自动重试。 */
    private final boolean retryable;

    /** 使用固定业务错误构造安全异常，errorCode 不允许为空。 */
    public BailianCallException(AiExecutionError errorCode, boolean retryable) {
        super(Objects.requireNonNull(errorCode, "errorCode").getMsg());
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    /** 返回供 SSE、数据库和审计统一记录的业务错误码。 */
    public AiExecutionError getErrorCode() { return errorCode; }

    /** 返回 UI 是否可向用户提供稍后重试操作。 */
    public boolean isRetryable() { return retryable; }

    /** 将嵌套异常转换为安全错误；优先保留已有业务分类，超时与中断统一为超时提示。 */
    public static BailianCallException from(Throwable failure) {
        boolean timeout = false;
        // 限制原因链深度，防止第三方异常的循环引用导致错误处理自身失去响应。
        for (int depth = 0; failure != null && depth < 32; depth++, failure = failure.getCause()) {
            if (failure instanceof BailianCallException) {
                return (BailianCallException) failure;
            }
            timeout |= failure instanceof InterruptedIOException; // 包括 SocketTimeoutException。
        }
        return new BailianCallException(timeout ? BAILIAN_TIMEOUT : BAILIAN_CALL_FAILED, true);
    }

    /** 按官方错误码和 HTTP 状态分类；原文仅在方法内判定，绝不保存、拼接或输出。 */
    static BailianCallException fromUpstream(int status, String code, String message) {
        String detail = (code + " " + message).toLowerCase(Locale.ROOT);
        if (contains(detail, "arrearage", "isv.out_of_service", "freetieronly", "free allocated quota exceeded",
                "free tier of the model has been exhausted", "insufficient balance", "good standing", "欠费", "余额不足")) {
            return new BailianCallException(BAILIAN_QUOTA_UNAVAILABLE, false);
        }
        // Throttling.AllocationQuota / insufficient_quota 表示 TPS/TPM 限流，不等同账户欠费。
        if (status == 429 || contains(detail, "throttl", "ratequota", "rate_limit", "ratelimit",
                "limit_requests", "limitrequests", "resourceexhausted", "insufficient_quota", "too many requests")) {
            return new BailianCallException(BAILIAN_RATE_LIMITED, true);
        }
        if (status == 408 || status == 504 || contains(detail, "timeout", "timed out")) {
            return new BailianCallException(BAILIAN_TIMEOUT, true);
        }
        if (status == 400 || status == 401 || status == 403 || status == 404
                || contains(detail, "invalidapikey", "invalid_api_key", "accessdenied", "access_denied",
                "not authorized", "modelnotfound", "model_not_found", "model_not_supported",
                "invalidparameter", "invalidapp", "appnotfound", "app.notfound", "appnotpublished",
                "workspacenotfound")) {
            return new BailianCallException(BAILIAN_CONFIG_INVALID, false);
        }
        return new BailianCallException(BAILIAN_CALL_FAILED, true);
    }

    /** 匹配受控错误特征，不把匹配结果之外的第三方内容带出分类边界。 */
    private static boolean contains(String detail, String... markers) {
        for (String marker : markers) {
            if (detail.contains(marker)) { return true; }
        }
        return false;
    }
}
