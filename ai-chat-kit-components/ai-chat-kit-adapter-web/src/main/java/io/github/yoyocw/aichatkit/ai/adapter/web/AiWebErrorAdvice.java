package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import javax.servlet.http.HttpServletResponse;

/** 只处理本模块Controller的异常，不改变宿主其它HTTP接口的错误约定。 */
@RestControllerAdvice(basePackageClasses = AiWebSingleController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
        type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "ai-chat-kit.ai", name = {"engine.enabled", "web.enabled"}, havingValue = "true")
public class AiWebErrorAdvice {
    /** @param activation 明确依赖启用检查，宿主扫描不能单独激活此Advice */
    public AiWebErrorAdvice(AiWebActivation activation) { java.util.Objects.requireNonNull(activation); }
    /** @param ex 明确分类的身份失败 @return 与分类对应的安全HTTP状态 */
    @ExceptionHandler(AiIdentityException.class)
    public ResponseEntity<AiWebResult<Void>> identity(AiIdentityException ex, HttpServletResponse response) {
        switch (ex.getError()) {
            case UNAUTHENTICATED: return error(401, "请登录后重试", response);
            case FORBIDDEN: return error(403, "无权执行当前操作", response);
            case DEPENDENCY_UNAVAILABLE: return error(503, "认证服务暂不可用", response);
            default: return error(500, "当前无法完成身份核验", response);
        }
    }
    /** @return 输入错误；不输出被拒绝的值、正文或校验对象 */
    @ExceptionHandler({AiWebInputException.class, javax.validation.ConstraintViolationException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<AiWebResult<Void>> invalid(Exception ex, HttpServletResponse response) {
        return error(400, "请求参数无效", response);
    }
    /** @param ex 明确的安全业务分类 @return 原业务码与固定安全文案，HTTP状态独立映射 */
    @ExceptionHandler(io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException.class)
    public ResponseEntity<AiWebResult<Void>> execution(
            io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException ex, HttpServletResponse response) {
        int status;
        switch (ex.getError()) {
            case CHAT_SHARE_NOT_EXISTS: case GROUP_SHARE_NOT_EXISTS:
            case CHAT_MESSAGE_NOT_EXISTS: case GROUP_MESSAGE_NOT_EXISTS: status = 404; break;
            case CHAT_MESSAGE_NOT_GENERATING: case GROUP_MESSAGE_NOT_GENERATING: status = 409; break;
            case SHARE_VALID_DAYS_INVALID: status = 400; break;
            case BAILIAN_RATE_LIMITED: status = 429; break;
            case BAILIAN_CALL_FAILED: case BAILIAN_QUOTA_UNAVAILABLE: status = 503; break;
            case BAILIAN_TIMEOUT: status = 504; break;
            case GROUP_WORKFLOW_OUTPUT_INVALID: case BAILIAN_RESPONSE_INCOMPLETE: status = 502; break;
            default: status = 500;
        }
        AiWebInputs.privateHeaders(response);
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(new AiWebResult<Void>(ex.getError().getCode(), ex.getError().getMsg(), null));
    }
    /** @return 未知错误固定500，禁止根据异常文字猜测未登录或回显内部信息 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AiWebResult<Void>> unknown(Exception ex, HttpServletResponse response) {
        return error(500, "当前无法完成操作，请稍后重试", response);
    }
    /** 安全头同样覆盖分享读取失败，避免缓存错误或泄露来源。 */
    private ResponseEntity<AiWebResult<Void>> error(int status, String msg, HttpServletResponse response) {
        AiWebInputs.privateHeaders(response);
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(new AiWebResult<Void>(status, msg, null));
    }
}
