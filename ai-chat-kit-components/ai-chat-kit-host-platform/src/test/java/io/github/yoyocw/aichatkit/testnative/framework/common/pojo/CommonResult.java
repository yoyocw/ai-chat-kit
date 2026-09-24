package io.github.yoyocw.aichatkit.testnative.framework.common.pojo;

/** Test-only native response shape; never used as a compatibility production type. */
public final class CommonResult<T> {
    private final Integer code;
    private final T data;
    private final String message;

    private CommonResult(int code, T data, String message) {
        this.code = code; this.data = data; this.message = message;
    }
    public static <T> CommonResult<T> success(T data) { return new CommonResult<>(0, data, null); }
    public static <T> CommonResult<T> error(int code) { return new CommonResult<>(code, null, null); }
    public static <T> CommonResult<T> error(int code, String message) {
        return new CommonResult<>(code, null, message);
    }
    public Integer getCode() { return code; }
    public T getData() { return data; }
    public boolean isSuccess() { return code == 0; }
    public static boolean isSuccess(Integer code) { return code != null && code == 0; }
    @Override public String toString() { return "CommonResult[" + message + "]"; }
}
