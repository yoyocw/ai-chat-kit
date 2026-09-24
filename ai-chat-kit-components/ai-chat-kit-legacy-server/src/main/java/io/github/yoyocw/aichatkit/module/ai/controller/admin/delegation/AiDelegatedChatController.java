package io.github.yoyocw.aichatkit.module.ai.controller.admin.delegation;

import io.github.yoyocw.aichatkit.compat.framework.apilog.core.annotation.ApiAccessLog;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiDelegatedChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.validation.Valid;

/** 业务后端调用的委托发送入口；服务层独立核验机器及用户身份，不信任预先透传身份。 */
@RestController
@RequestMapping("/ai/delegated")
@RequiredArgsConstructor
public class AiDelegatedChatController {
    /** 双重认证与现有发送流程适配。 */
    private final AiDelegatedChatService delegatedChatService;

    /** 发送单聊；请求头含敏感证明，禁止访问日志正文采集。 */
    @PostMapping(value = "/single/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiAccessLog(enable = false)
    public StreamingResponseBody sendSingle(@Valid @RequestBody AiChatSendReqVO request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-AI-User-Delegation") String delegation) {
        return delegatedChatService.sendSingle(request, authorization, delegation);
    }

    /** 发送群聊；会话归属继续由既有群聊服务验证。 */
    @PostMapping(value = "/group/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiAccessLog(enable = false)
    public StreamingResponseBody sendGroup(@Valid @RequestBody AiGroupChatSendReqVO request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-AI-User-Delegation") String delegation) {
        return delegatedChatService.sendGroup(request, authorization, delegation);
    }
}
