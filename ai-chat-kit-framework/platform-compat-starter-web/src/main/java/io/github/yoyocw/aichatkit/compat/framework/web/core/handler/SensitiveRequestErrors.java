package io.github.yoyocw.aichatkit.compat.framework.web.core.handler;

import io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 服务端精确标记的敏感请求异常出口；不读取异常消息、原始请求体或凭据，不改变其它请求。 */
public final class SensitiveRequestErrors {
    /** 仅服务端 Filter 设置，HTTP 头、参数及 JSON 字段不能写入此属性。 */
    private static final String ATTRIBUTE = SensitiveRequestErrors.class.getName() + ".protected";
    /** 与当前派发绑定的响应，只由前置保护过滤器保存。 */
    private static final String RESPONSE_ATTRIBUTE = SensitiveRequestErrors.class.getName() + ".response";

    private SensitiveRequestErrors() { }

    /** @param request 已由可信路由配置匹配的请求 @param response 当前派发响应；标记不能由外部输入创建。 */
    public static void mark(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute(ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(RESPONSE_ATTRIBUTE, response);
    }

    /** @param request 当前请求 @return 是否已由服务端标记为敏感请求。 */
    public static boolean isMarked(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(ATTRIBUTE));
    }

    /**
     * 保留业务异常的数字错误码，但消息统一固定，未知故障固定 500；不记录或返回异常内容。
     * @param error 原异常仅用于判断类型和安全数字码
     * @return 脱敏结果，不含 cause/message/body
     */
    public static CommonResult<?> sanitize(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 4; depth++, current = current.getCause()) {
            if (current instanceof ServiceException) {
                return CommonResult.error(((ServiceException) current).getCode(), "请求未获授权或处理失败");
            }
            if (current instanceof HttpMessageNotReadableException) {
                return CommonResult.error(400, "请求格式无效");
            }
            if (current instanceof AccessDeniedException) { return CommonResult.error(403, "请求未获授权"); }
        }
        return CommonResult.error(500, "服务暂时无法处理请求");
    }

    /** @param request 已标记请求 @param error 不记录的原异常 @return 安全错误，同时在未提交时保留合理 HTTP 状态。 */
    public static CommonResult<?> sanitize(HttpServletRequest request, Throwable error) {
        CommonResult<?> result = sanitize(error);
        Object response = request.getAttribute(RESPONSE_ATTRIBUTE);
        if (response instanceof HttpServletResponse && !((HttpServletResponse) response).isCommitted()) {
            int code = result.getCode();
            ((HttpServletResponse) response).setStatus(code >= 400 && code <= 599 ? code : 400);
            ((HttpServletResponse) response).setHeader("Cache-Control", "no-store");
        }
        return result;
    }

    /**
     * 输出安全错误且禁止缓存，已提交响应不得再次改写。
     * @param response 当前响应
     * @param error 不得记录的原异常
     * @throws IOException 连接已断开等响应写入失败，不携带请求内容
     */
    public static void write(HttpServletResponse response, Throwable error) throws IOException {
        if (response.isCommitted()) { return; }
        CommonResult<?> result = sanitize(error);
        int code = result.getCode();
        response.setStatus(code >= 400 && code <= 599 ? code : 400);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJsonString(result));
    }
}
