package io.github.yoyocw.aichatkit.ai.starter.host;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareGrant;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;

import java.util.Objects;
import java.util.UUID;

/** 登录用户的分享管理入口；单群聊创建、撤销分别授权，不开放 HTTP 或匿名管理操作。 */
public final class AiHostConversationShareService {
    /** 实时宿主会话及固定分享操作权限。 */
    private final AiHostAuthenticationBridge authenticationBridge;
    /** 最终核验身份、归属及分享期限的容器服务。 */
    private final AiConversationShareService shareService;

    /**
     * @param authenticationBridge 真实宿主认证边界
     * @param shareService 容器管理的分享引擎
     * @throws NullPointerException 必要服务缺失
     */
    public AiHostConversationShareService(AiHostAuthenticationBridge authenticationBridge,
                                         AiConversationShareService shareService) {
        this.authenticationBridge = Objects.requireNonNull(authenticationBridge, "宿主认证桥接不能为空");
        this.shareService = Objects.requireNonNull(shareService, "会话分享引擎不能为空");
    }

    /**
     * 创建或复用有效分享；分享动态展示当前已完成消息，不是创建时的固定快照。
     * @param mode SINGLE 或 GROUP，使用对应固定分享创建权限
     * @param conversationId 正数会话编号，归属由引擎再次核验
     * @param validDays 可空的有效天数，默认值及范围由引擎统一检查
     * @param expectedActorId 仅用于比对真实登录身份的预期用户
     * @return 分享码、公开地址及失效时间；分享码具有访问能力，不能写入日志
     * @throws IllegalStateException 认证、归属、创建或事务失败
     */
    public AiShareGrant issue(AiChatMode mode, Long conversationId, Integer validDays, String expectedActorId) {
        requireId(conversationId);
        AiHostAction action = select(mode, AiHostAction.CHAT_SHARE_CREATE, AiHostAction.GROUP_SHARE_CREATE);
        return shareService.issueForContext(mode, conversationId, validDays, authorize(expectedActorId, action));
    }

    /**
     * @param mode SINGLE 或 GROUP，撤销权限不使用创建或发送权限替代
     * @param conversationId 正数会话编号，必须属于当前真实身份
     * @param expectedActorId 仅用于与真实登录身份比对的预期用户
     * @throws IllegalStateException 认证、归属或撤销失败；不返回伪成功
     */
    public void revoke(AiChatMode mode, Long conversationId, String expectedActorId) {
        requireId(conversationId);
        AiHostAction action = select(mode, AiHostAction.CHAT_SHARE_REVOKE, AiHostAction.GROUP_SHARE_REVOKE);
        shareService.revokeForContext(mode, conversationId, authorize(expectedActorId, action));
    }

    /** 完成真实授权后构造归属匹配条件；引擎不能把此快照当作认证证明。 */
    private AiInvocationContext authorize(String expectedActorId, AiHostAction action) {
        AiHostSession session = authenticationBridge.authorizeCurrent(expectedActorId, action);
        if (session.getExpiresAtMillis() <= System.currentTimeMillis()) {
            throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED);
        }
        return new AiInvocationContext(session.getNamespace(), session.getTenantId(), session.getActorId(),
                UUID.randomUUID().toString());
    }

    /** 按模式在服务端固定的权限间选择，禁止空模式或未知模式。 */
    private AiHostAction select(AiChatMode mode, AiHostAction single, AiHostAction group) {
        if (mode == AiChatMode.SINGLE) { return single; }
        if (mode == AiChatMode.GROUP) { return group; }
        throw new IllegalArgumentException("聊天模式无效");
    }

    /** 正数会话编号只是查询条件，不能替代归属校验。 */
    private void requireId(Long id) {
        if (id == null || id <= 0) { throw new IllegalArgumentException("会话编号无效"); }
    }
}
