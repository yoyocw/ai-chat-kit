package io.github.yoyocw.aichatkit.module.ai.controller.admin.delegation;

import io.github.yoyocw.aichatkit.compat.framework.apilog.core.annotation.ApiAccessLog;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiDelegatedStopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/** 固定模式的业务后端停止入口；沿用默认登录过滤，最终授权只信任专用票据核验结果。 */
@RestController
@RequestMapping("/ai/delegated")
@RequiredArgsConstructor
public class AiDelegatedStopController {
    /** 独立消费身份核验及本地停止协调。 */
    private final AiDelegatedStopService service;

    /** @param response 本控制器响应，参数绑定前禁止缓存。 */
    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    /** 单聊模式由路径固定，正文和外部登录头不能改变消费身份。 */
    @PostMapping("/single/stop")
    @ApiAccessLog(enable = false)
    public CommonResult<Boolean> single(@Valid @RequestBody AiDelegatedStopReqVO request,
                                       @RequestHeader("X-AI-Stop-Ticket") String ticket) {
        service.stop(AiChatMode.SINGLE, request.getMessageId(), ticket);
        return CommonResult.success(true);
    }

    /** 群聊停止仍执行原助手来源、归属与状态CAS。 */
    @PostMapping("/group/stop")
    @ApiAccessLog(enable = false)
    public CommonResult<Boolean> group(@Valid @RequestBody AiDelegatedStopReqVO request,
                                      @RequestHeader("X-AI-Stop-Ticket") String ticket) {
        service.stop(AiChatMode.GROUP, request.getMessageId(), ticket);
        return CommonResult.success(true);
    }
}
