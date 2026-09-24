package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatShareRespVO;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Pattern;

import static io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult.success;

/**
 * AI 群聊公开分享接口，仅凭不可枚举分享码返回脱敏后的成员和已完成消息。
 */
@Tag(name = "AI 群聊公开分享")
@RestController
@RequestMapping("/ai/group-chat/share")
@Validated
@PermitAll
@TenantIgnore
@RequiredArgsConstructor
public class AiGroupChatShareController {

    /** AI 群聊领域服务。 */
    private final AiGroupChatService groupChatService;

    /**
     * 获取公开分享群聊。
     *
     * @param shareCode 32 位不可枚举分享码
     * @return 脱敏群聊标题、成员及已完成消息
     */
    @GetMapping("/{shareCode}")
    @PermitAll
    @Operation(summary = "公开查看 AI 群聊分享")
    public CommonResult<AiGroupChatShareRespVO> getSharedConversation(@PathVariable("shareCode")
                                                                          @Pattern(regexp = "[0-9a-f]{32}", message = "分享码格式不正确") String shareCode,
                                                                      HttpServletResponse response) {
        // 公开分享可能包含业务对话，禁止中间缓存、搜索引擎收录及来源地址外泄。
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Robots-Tag", "noindex");
        response.setHeader("Referrer-Policy", "no-referrer");
        return success(groupChatService.getSharedConversation(shareCode));
    }
}
