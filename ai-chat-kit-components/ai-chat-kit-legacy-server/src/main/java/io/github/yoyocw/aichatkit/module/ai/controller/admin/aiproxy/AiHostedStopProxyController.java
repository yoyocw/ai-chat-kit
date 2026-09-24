package io.github.yoyocw.aichatkit.module.ai.controller.admin.aiproxy;

import io.github.yoyocw.aichatkit.compat.framework.apilog.core.annotation.ApiAccessLog;
import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiHostedStopProxyService;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.delegation.AiDelegatedStopReqVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

/** AI 自有停止入口，承接原业务停止 URL；缺少部署配置按需拒绝，不使用旧 system 票据。 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/ai/business-proxy", "/system/ai-proxy"})
public class AiHostedStopProxyController {
    /** 真实身份、独立票据消费及既有来源/CAS流程。 */
    private final ObjectProvider<AiHostedStopProxyService> serviceProvider;

    /** 单聊只使用既有停止权限，票据不返回浏览器。 */
    @PostMapping("/single/stop")
    @PreAuthorize("@ss.hasPermission('ai:chat:stopSeed')")
    @ApiAccessLog(enable = false)
    public CommonResult<Boolean> single(@Valid @RequestBody AiDelegatedStopReqVO request,
            @RequestHeader("Authorization") String authorization) {
        service().stop(AiChatMode.SINGLE, request.getMessageId(), authorization);
        return CommonResult.success(true);
    }

    /** 群聊只使用既有停止权限，目标助手归属由事务边界再次复查。 */
    @PostMapping("/group/stop")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:message:stop')")
    @ApiAccessLog(enable = false)
    public CommonResult<Boolean> group(@Valid @RequestBody AiDelegatedStopReqVO request,
            @RequestHeader("Authorization") String authorization) {
        service().stop(AiChatMode.GROUP, request.getMessageId(), authorization);
        return CommonResult.success(true);
    }

    /** 未启用时明确拒绝，停止 URL 不重定向到旧服务。 */
    private AiHostedStopProxyService service() {
        AiHostedStopProxyService service = serviceProvider.getIfAvailable();
        if (service == null) { throw new IllegalStateException("AI 授权停止未启用"); }
        return service;
    }
}
