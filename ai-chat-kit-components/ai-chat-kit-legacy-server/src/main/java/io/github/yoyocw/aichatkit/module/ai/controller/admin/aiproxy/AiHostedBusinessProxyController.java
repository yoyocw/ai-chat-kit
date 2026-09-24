package io.github.yoyocw.aichatkit.module.ai.controller.admin.aiproxy;

import io.github.yoyocw.aichatkit.compat.framework.apilog.core.annotation.ApiAccessLog;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiHostedBusinessProxyService;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.aiproxy.vo.AiProxySendReqVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import javax.validation.Valid;

/** AI 自有业务代理，保留业务发送 URL；缺少部署配置时按需拒绝，不回退 system。 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/ai/business-proxy", "/system/ai-proxy"})
public class AiHostedBusinessProxyController {
    /** 真实用户复核、专用机器凭据生命周期及 SSE 转发。 */
    private final ObjectProvider<AiHostedBusinessProxyService> serviceProvider;

    /** 单聊保持原功能权限，不采集含对话内容的访问日志。 */
    @PostMapping(value = "/single/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasPermission('ai:chat:seed')")
    @ApiAccessLog(enable = false)
    public StreamingResponseBody single(@Valid @RequestBody AiProxySendReqVO request,
            @RequestHeader("Authorization") String authorization) {
        return service().send(request, authorization, false);
    }

    /** 群聊保持原功能权限，会话归属继续由下游 AI 服务核对。 */
    @PostMapping(value = "/group/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasPermission('aigroup:chat:message:send')")
    @ApiAccessLog(enable = false)
    public StreamingResponseBody group(@Valid @RequestBody AiProxySendReqVO request,
            @RequestHeader("Authorization") String authorization) {
        return service().send(request, authorization, true);
    }

    /** 未启用时保留明确拒绝行为，不装配需要秘密配置的发送服务。 */
    private AiHostedBusinessProxyService service() {
        AiHostedBusinessProxyService service = serviceProvider.getIfAvailable();
        if (service == null) { throw new IllegalStateException("AI 业务代理未启用"); }
        return service;
    }
}
