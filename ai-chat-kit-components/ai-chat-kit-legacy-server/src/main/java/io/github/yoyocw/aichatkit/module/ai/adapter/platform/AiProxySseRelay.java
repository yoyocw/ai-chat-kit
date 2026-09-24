package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 按完整 SSE 事件转发，限制事件大小并要求明确的 done，防止提前 EOF 留下加载状态。 */
public final class AiProxySseRelay {
    /**
     * 转发到 done 为止，不持久化或记录事件正文。
     * @param input AI 响应流，由调用者关闭
     * @param output 浏览器响应流，由容器关闭
     * @throws IOException 超长、提前结束或客户端断开
     */
    public static void transfer(InputStream input, OutputStream output) throws IOException {
        InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        StringBuilder frame = new StringBuilder();
        int total = 0;
        int character;
        while ((character = reader.read()) != -1) {
            if (++total > 8 * 1024 * 1024 || frame.length() >= 1024 * 1024) {
                throw new IOException("AI 流式响应超出容量限制");
            }
            if (character == '\r') {
                continue;
            }
            frame.append((char) character);
            int length = frame.length();
            if (length < 2 || frame.charAt(length - 1) != '\n' || frame.charAt(length - 2) != '\n') {
                continue;
            }
            String event = frame.toString();
            output.write(event.getBytes(StandardCharsets.UTF_8));
            output.flush();
            if ((event.startsWith("event:done\n") || event.startsWith("event: done\n"))
                    && event.contains("\ndata:")) {
                return;
            }
            frame.setLength(0);
        }
        throw new IOException("AI 流式响应提前结束");
    }

    /** 只写固定错误；done.status 沿用聊天消息协议的整数 3（失败），不暴露下游异常。 */
    public static void failure(OutputStream output) throws IOException {
        output.write(("event:error\ndata:{\"message\":\"当前对话服务不可用，请重试\"}\n\n"
                + "event:done\ndata:{\"status\":3}\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private AiProxySseRelay() { }
}
