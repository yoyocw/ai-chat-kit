package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationPinReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatMessageRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatService;
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
 * AI 智能对话管理端接口，提供会话、消息与流式问答能力。
 */
@Tag(name = "AI 智能对话")
@RestController
@RequestMapping("/ai/chat")
@Validated
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/conversation")
    @Operation(summary = "创建 AI 对话")
    @PreAuthorize("@ss.hasPermission('ai:chat:createConversation')")
    public CommonResult<Long> createConversation() {
        return success(aiChatService.createConversation(requiredLoginUserId()));
    }

    @PutMapping("/conversation")
    @Operation(summary = "重命名 AI 对话")
    @PreAuthorize("@ss.hasPermission('ai:chat:updateConversation')")
    public CommonResult<Boolean> updateConversation(@Valid @RequestBody AiChatConversationUpdateReqVO reqVO) {
        aiChatService.updateConversation(reqVO, requiredLoginUserId());
        return success(true);
    }

    @PutMapping("/conversation/pin")
    @Operation(summary = "置顶或取消置顶 AI 对话")
    @PreAuthorize("@ss.hasPermission('ai:chat:pinConversation')")
    public CommonResult<Boolean> pinConversation(@Valid @RequestBody AiChatConversationPinReqVO reqVO) {
        aiChatService.pinConversation(reqVO, requiredLoginUserId());
        return success(true);
    }

    @PostMapping("/conversation/share")
    @Operation(summary = "创建或复用 AI 对话公开分享")
    @PreAuthorize("@ss.hasPermission('ai:chat:shareConversation')")
    public CommonResult<AiChatConversationShareRespVO> shareConversation(
            @Valid @RequestBody AiChatConversationShareReqVO reqVO) {
        return success(aiChatService.shareConversation(reqVO, requiredLoginUserId()));
    }

    @DeleteMapping("/conversation/share")
    @Operation(summary = "取消 AI 对话公开分享")
    @PreAuthorize("@ss.hasPermission('ai:chat:cancelConversationShare')")
    @Parameter(name = "id", description = "会话编号", required = true)
    public CommonResult<Boolean> cancelConversationShare(@RequestParam("id") @NotNull Long id) {
        aiChatService.cancelConversationShare(id, requiredLoginUserId());
        return success(true);
    }

    @DeleteMapping("/conversation")
    @Operation(summary = "删除 AI 对话")
    @PreAuthorize("@ss.hasPermission('ai:chat:delete')")
    @Parameter(name = "id", description = "会话编号", required = true)
    public CommonResult<Boolean> deleteConversation(@RequestParam("id") @NotNull Long id) {
        aiChatService.deleteConversation(id, requiredLoginUserId());
        return success(true);
    }

    @GetMapping("/conversation/list")
    @Operation(summary = "获得当前用户的 AI 对话列表")
    @PreAuthorize("@ss.hasPermission('ai:chat:get')")
    public CommonResult<List<AiChatConversationRespVO>> getConversationList() {
        return success(aiChatService.getConversationList(requiredLoginUserId()));
    }

    @GetMapping("/message/list")
    @Operation(summary = "获得 AI 对话消息列表")
    @Parameter(name = "conversationId", description = "会话编号", required = true)
    @PreAuthorize("@ss.hasPermission('ai:chat:list')")
    public CommonResult<List<AiChatMessageRespVO>> getMessageList(
            @RequestParam("conversationId") @NotNull Long conversationId) {
        return success(aiChatService.getMessageList(conversationId, requiredLoginUserId()));
    }

    @PostMapping(value = "/message/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "向百炼应用发送消息并流式返回")
    @PreAuthorize("@ss.hasPermission('ai:chat:seed')")
    public StreamingResponseBody sendMessage(@Valid @RequestBody AiChatSendReqVO reqVO) {
        return aiChatService.sendMessage(reqVO, requiredLoginUserId());
    }

    @PostMapping("/message/stop")
    @Operation(summary = "停止生成 AI 回复")
    @PreAuthorize("@ss.hasPermission('ai:chat:stopSeed')")
    @Parameter(name = "messageId", description = "生成中的助手消息编号", required = true)
    public CommonResult<Boolean> stopMessage(@RequestParam("messageId") @NotNull Long messageId) {
        aiChatService.stopMessage(messageId, requiredLoginUserId());
        return success(true);
    }

    private Long requiredLoginUserId() {
        return Objects.requireNonNull(getLoginUserId(), "登录用户编号不能为空");
    }
}
