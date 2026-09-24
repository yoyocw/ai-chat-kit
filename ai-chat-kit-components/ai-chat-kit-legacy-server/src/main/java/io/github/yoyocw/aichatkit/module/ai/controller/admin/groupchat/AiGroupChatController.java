package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationPinReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatAgentRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationCreateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatMemberUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatMessageRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

import static io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult.success;
import static io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * AI 群聊管理端接口，将页面群聊能力委托给服务端百炼工作流编排。
 */
@Tag(name = "AI 多智能体群聊")
@RestController
@RequestMapping("/ai/group-chat")
@Validated
@RequiredArgsConstructor
public class AiGroupChatController {

    /** AI 群聊领域服务。 */
    private final AiGroupChatService groupChatService;

    @GetMapping("/agent/list")
    @Operation(summary = "获得可用群聊智能体列表")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:agent:list')")
    public CommonResult<List<AiGroupChatAgentRespVO>> getAgentList() {
        return success(groupChatService.getAgentList());
    }

    @PostMapping("/conversation")
    @Operation(summary = "创建 AI 群聊")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:create')")
    public CommonResult<Long> createConversation(
            @Valid @RequestBody AiGroupChatConversationCreateReqVO reqVO) {
        return success(groupChatService.createConversation(reqVO, requiredLoginUserId()));
    }

    @PutMapping("/conversation")
    @Operation(summary = "重命名 AI 群聊")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:update')")
    public CommonResult<Boolean> updateConversation(
            @Valid @RequestBody AiGroupChatConversationUpdateReqVO reqVO) {
        groupChatService.updateConversation(reqVO, requiredLoginUserId());
        return success(true);
    }

    @PutMapping("/conversation/pin")
    @Operation(summary = "置顶或取消置顶 AI 群聊")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:pin')")
    public CommonResult<Boolean> pinConversation(@Valid @RequestBody AiChatConversationPinReqVO reqVO) {
        groupChatService.pinConversation(reqVO, requiredLoginUserId());
        return success(true);
    }

    @PostMapping("/conversation/share")
    @Operation(summary = "创建或复用 AI 群聊公开分享")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:share')")
    public CommonResult<AiChatConversationShareRespVO> shareConversation(
            @Valid @RequestBody AiChatConversationShareReqVO reqVO) {
        return success(groupChatService.shareConversation(reqVO, requiredLoginUserId()));
    }

    @DeleteMapping("/conversation/share")
    @Operation(summary = "取消 AI 群聊公开分享")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:cancelShare')")
    @Parameter(name = "id", description = "群聊会话编号", required = true)
    public CommonResult<Boolean> cancelConversationShare(@RequestParam("id") @NotNull Long id) {
        groupChatService.cancelConversationShare(id, requiredLoginUserId());
        return success(true);
    }

    @PutMapping("/conversation/members")
    @Operation(summary = "调整 AI 群聊候选成员")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:updateMembers')")
    public CommonResult<Boolean> updateMembers(@Valid @RequestBody AiGroupChatMemberUpdateReqVO reqVO) {
        groupChatService.updateMembers(reqVO, requiredLoginUserId());
        return success(true);
    }

    @DeleteMapping("/conversation")
    @Operation(summary = "删除 AI 群聊")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:delete')")
    @Parameter(name = "id", description = "群聊会话编号", required = true)
    public CommonResult<Boolean> deleteConversation(@RequestParam("id") @NotNull Long id) {
        groupChatService.deleteConversation(id, requiredLoginUserId());
        return success(true);
    }

    @GetMapping("/conversation/list")
    @Operation(summary = "获得当前用户 AI 群聊列表")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:conversation:list')")
    public CommonResult<List<AiGroupChatConversationRespVO>> getConversationList() {
        return success(groupChatService.getConversationList(requiredLoginUserId()));
    }

    @GetMapping("/message/list")
    @Operation(summary = "获得 AI 群聊历史消息")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:message:list')")
    public CommonResult<List<AiGroupChatMessageRespVO>> getMessageList(
            @RequestParam("conversationId") @NotNull Long conversationId) {
        return success(groupChatService.getMessageList(conversationId, requiredLoginUserId()));
    }

    @PostMapping(value = "/message/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送群聊消息并流式返回多智能体回复")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:message:send')")
    public StreamingResponseBody sendMessage(@Valid @RequestBody AiGroupChatSendReqVO reqVO) {
        return groupChatService.sendMessage(reqVO, requiredLoginUserId());
    }

    @PostMapping("/message/stop")
    @Operation(summary = "停止 AI 群聊生成")
    @PreAuthorize("@ss.hasPermission('aigroup:chat:message:stop')")
    public CommonResult<Boolean> stopMessage(@RequestParam("messageId") @NotNull Long messageId) {
        groupChatService.stopMessage(messageId, requiredLoginUserId());
        return success(true);
    }

    private Long requiredLoginUserId() {
        return Objects.requireNonNull(getLoginUserId(), "登录用户编号不能为空");
    }
}
