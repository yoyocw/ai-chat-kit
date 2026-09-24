package io.github.yoyocw.aichatkit.ai.adapter.web;

/** HTTP 响应信封；不会包装 SSE 字节流。 */
public final class AiWebResult<T> {
    /** 成功为0，失败采用安全HTTP分类码。 */
    private final int code;
    /** 面向用户的安全文案。 */
    private final String msg;
    /** 类型明确的业务结果，失败为空。 */
    private final T data;
    /** @param code 结果码 @param msg 安全文案 @param data 业务结果 */
    public AiWebResult(int code, String msg, T data) { this.code = code; this.msg = msg; this.data = data; }
    /** @return 成功结果 */
    public static <T> AiWebResult<T> success(T data) { return new AiWebResult<T>(0, "操作成功", data); }
    /** @return 结果码 */ public int getCode() { return code; }
    /** @return 安全文案 */ public String getMsg() { return msg; }
    /** @return 业务结果 */ public T getData() { return data; }
}

