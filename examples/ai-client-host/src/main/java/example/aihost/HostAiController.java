package example.aihost;

import io.github.yoyocw.aichatkit.aiclient.AiChatMode;
import io.github.yoyocw.aichatkit.aiclient.AiClient;
import io.github.yoyocw.aichatkit.aiclient.AiSendRequest;
import io.github.yoyocw.aichatkit.aiclient.AiStopRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 宿主自行定义URL和异常约定；Starter不向宿主注入Controller或安全过滤器。 */
@RestController
@RequestMapping("/host/ai")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.client", name = "enabled", havingValue = "true")
public class HostAiController {
    /** 自动装配的客户端，经兼容网关或 AI 服务调用代理入口。 */
    private final AiClient client;

    /** @param client 启用时必须存在的客户端 */
    public HostAiController(AiClient client) { this.client = client; }

    /** @param input 单聊请求 @param response 同步SSE输出 @throws IOException 连接失败或用户断开 */
    @PostMapping(value = "/single/send", produces = "text/event-stream")
    public void single(@RequestBody AiSendRequest input, HttpServletResponse response) throws IOException {
        stream(AiChatMode.SINGLE, input, response);
    }

    /** @param input 群聊请求 @param response 同步SSE输出 @throws IOException 连接失败或用户断开 */
    @PostMapping(value = "/group/send", produces = "text/event-stream")
    public void group(@RequestBody AiSendRequest input, HttpServletResponse response) throws IOException {
        stream(AiChatMode.GROUP, input, response);
    }

    /** @param input 单聊目标 @param response 宿主响应 @return 远端明确确认的逻辑停止 */
    @PostMapping("/single/stop")
    public boolean singleStop(@RequestBody AiStopRequest input, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        client.stop(AiChatMode.SINGLE, input.getMessageId());
        return true;
    }

    /** @param input 群聊目标 @param response 宿主响应 @return 远端明确确认的逻辑停止 */
    @PostMapping("/group/stop")
    public boolean groupStop(@RequestBody AiStopRequest input, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        client.stop(AiChatMode.GROUP, input.getMessageId());
        return true;
    }

    /** 在同一请求线程取身份，避免异步线程丢失宿主认证上下文。 */
    private void stream(AiChatMode mode, AiSendRequest input, HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        client.stream(mode, input, response.getOutputStream());
    }
}
