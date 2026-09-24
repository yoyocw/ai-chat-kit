package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.ai.starter.host.*;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.List;
import java.util.stream.Collectors;

/** 单聊10项管理操作与公开分享；入口启用不替代宿主认证过滤器。 */
@RestController
@Validated
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
        type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "ai-chat-kit.ai", name = {"engine.enabled", "web.enabled"}, havingValue = "true")
@org.springframework.context.annotation.Conditional(AiWebSingleCondition.class)
@RequestMapping("${ai-chat-kit.ai.web.path-prefix:/admin-api/ai}/chat")
public class AiWebSingleController {
    /** 请求线程真实身份捕获。 */
    private final AiWebIdentity identity;
    /** 已授权执行门面。 */
    private final AiHostSingleChatService execution;
    /** 会话管理门面。 */
    private final AiHostConversationManagementService management;
    /** 登录用户分享管理门面。 */
    private final AiHostConversationShareService shares;
    /** 独立公开分享读取；不构造匿名用户身份。 */
    private final AiPublicConversationShareService publicShares;

    /** @param activation 明确启用检查 @param identity 真实身份 @param execution 执行门面 @param management 管理门面
     * @param shares 分享管理 @param publicShares 公开读取 */
    public AiWebSingleController(AiWebActivation activation, AiWebIdentity identity, AiHostSingleChatService execution,
            AiHostConversationManagementService management, AiHostConversationShareService shares,
            AiPublicConversationShareService publicShares) {
        java.util.Objects.requireNonNull(activation, "activation");
        this.identity = identity; this.execution = execution; this.management = management;
        this.shares = shares; this.publicShares = publicShares;
    }

    /** @return 当前真实用户新建的会话编号 */
    @PostMapping("/conversation")
    public AiWebResult<Long> create() {
        return AiWebResult.success(management.createSingle(identity.currentActor()));
    }

    /** @param request 会话及新标题 @return 修改成功 */
    @PutMapping("/conversation")
    public AiWebResult<Boolean> rename(@Valid @RequestBody AiWebRenameRequest request) {
        
        management.rename(AiChatMode.SINGLE, request.getId(), request.getTitle(), identity.currentActor());
        return AiWebResult.success(true);
    }

    /** @param request 会话置顶意图 @return 修改成功 */
    @PutMapping("/conversation/pin")
    public AiWebResult<Boolean> pin(@Valid @RequestBody AiWebPinRequest request) {
        management.pin(AiChatMode.SINGLE, request.getId(), request.getPinned(), identity.currentActor());
        return AiWebResult.success(true);
    }

    /** @param request 会话及1至30天有效期 @return 受控公开地址 */
    @PostMapping("/conversation/share")
    public AiWebResult<AiWebShareGrantResponse> share(@Valid @RequestBody AiWebShareRequest request) {
        return AiWebResult.success(new AiWebShareGrantResponse(shares.issue(AiChatMode.SINGLE,
                request.getId(), request.getValidDays(), identity.currentActor())));
    }

    /** @param id 正数会话编号 @return 撤销成功 */
    @DeleteMapping("/conversation/share")
    public AiWebResult<Boolean> revoke(@RequestParam("id") @Positive Long id) {
        shares.revoke(AiChatMode.SINGLE, id, identity.currentActor());
        return AiWebResult.success(true);
    }

    /** @param id 正数会话编号 @return 删除成功 */
    @DeleteMapping("/conversation")
    public AiWebResult<Boolean> delete(@RequestParam("id") @Positive Long id) {
        management.delete(AiChatMode.SINGLE, id, identity.currentActor());
        return AiWebResult.success(true);
    }

    /** @return 当前用户的会话；缺失成员快照保留状态及恢复标识 */
    @GetMapping("/conversation/list")
    public AiWebResult<List<AiWebConversationResponse>> conversations() {
        return AiWebResult.success(management.listConversations(AiChatMode.SINGLE, identity.currentActor())
                .stream().map(AiWebConversationResponse::new).collect(Collectors.toList()));
    }

    /** @param conversationId 正数会话编号 @return 授权历史，responseData保持JSON字符串 */
    @GetMapping("/message/list")
    public AiWebResult<List<AiWebMessageResponse>> messages(@RequestParam("conversationId") @Positive Long conversationId) {
        return AiWebResult.success(management.listMessages(AiChatMode.SINGLE, conversationId, identity.currentActor())
                .stream().map(AiWebMessageResponse::new).collect(Collectors.toList()));
    }

    /** @param request 本轮问题 @return 已同步认证准备的SSE，不添加异步身份捕获 */
    @PostMapping(value = "/message/send", produces = "text/event-stream")
    public StreamingResponseBody send(@Valid @RequestBody AiWebSingleSendRequest request) {
        // 地图默认关闭；显式请求交由真实宿主业务扩展处理，默认plain适配器负责拒绝不支持的能力。
        return execution.send(request, identity.currentActor());
    }

    /** @param messageId 正数助手消息编号 @return 停止成功 */
    @PostMapping("/message/stop")
    public AiWebResult<Boolean> stop(@RequestParam("messageId") @Positive Long messageId) {
        execution.stop(messageId, identity.currentActor());
        return AiWebResult.success(true);
    }

    /** @param shareCode 受控分享码 @param response 禁缓存安全头 @return 动态公开内容 */
    @GetMapping("/share/{shareCode}")
    public AiWebResult<AiWebSharedConversationResponse> readShare(@PathVariable("shareCode") String shareCode,
            javax.servlet.http.HttpServletResponse response) {
        AiWebInputs.privateHeaders(response);
        AiWebInputs.shareCode(shareCode);
        return AiWebResult.success(new AiWebSharedConversationResponse(publicShares.read(AiChatMode.SINGLE, shareCode)));
    }
}
