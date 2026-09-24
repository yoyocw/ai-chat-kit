package io.github.yoyocw.aichatkit.ai.starter.host;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationManagementService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MIN_MEMBER_COUNT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MAX_MEMBER_COUNT;

/**
 * 宿主会话管理服务入口；每个操作分别授权，再由引擎复核三元身份后按真实归属访问存储。
 * 单聊和群聊权限分离，客户端不能传权限编码；不开放 HTTP，不提供分享能力。
 */
public final class AiHostConversationManagementService {
    /** 当前真实会话及操作权限核验。 */
    private final AiHostAuthenticationBridge authenticationBridge;
    /** 容器管理的会话事务服务，负责归属、历史顺序及删除后的取消行为。 */
    private final AiConversationManagementService managementService;

    /** @param expectedActorId 与真实当前用户匹配的标识 @return 独立创建权限核验后的真实空会话编号 */
    public Long createSingle(String expectedActorId) {
        return managementService.createSingleForContext(authorize(expectedActorId, AiHostAction.CHAT_CREATE));
    }

    /**
     * @param authenticationBridge 宿主真实认证边界
     * @param managementService 容器管理的引擎管理服务
     * @throws NullPointerException 必要服务缺失
     */
    public AiHostConversationManagementService(AiHostAuthenticationBridge authenticationBridge,
                                               AiConversationManagementService managementService) {
        this.authenticationBridge = Objects.requireNonNull(authenticationBridge, "宿主认证桥接不能为空");
        this.managementService = Objects.requireNonNull(managementService, "会话管理引擎不能为空");
    }

    /**
     * @param mode SINGLE 或 GROUP，决定固定的会话读取权限
     * @param expectedActorId 与当前真实登录身份比对的预期用户
     * @return 当前三元归属内按置顶及时间降序排列的会话
     * @throws IllegalStateException 未授权或授权与执行身份不一致
     */
    public List<AiConversationView> listConversations(AiChatMode mode, String expectedActorId) {
        AiHostAction action = select(mode, AiHostAction.CHAT_CONVERSATION_LIST, AiHostAction.GROUP_CONVERSATION_LIST);
        return managementService.listConversationsForContext(mode, authorize(expectedActorId, action));
    }

    /**
     * @param mode SINGLE 或 GROUP，消息历史权限不复用会话列表或发送权限
     * @param conversationId 正数会话编号，归属由引擎复核
     * @param expectedActorId 与真实登录身份比对的预期用户
     * @return 当前用户该会话内按消息编号升序排列的历史
     * @throws IllegalStateException 未授权或授权与执行身份不一致
     */
    public List<AiMessageView> listMessages(AiChatMode mode, Long conversationId, String expectedActorId) {
        requireId(conversationId);
        AiHostAction action = select(mode, AiHostAction.CHAT_MESSAGE_LIST, AiHostAction.GROUP_MESSAGE_LIST);
        return managementService.listMessagesForContext(mode, conversationId, authorize(expectedActorId, action));
    }

    /**
     * @param mode 需要改名的聊天模式
     * @param conversationId 正数会话编号
     * @param title 新标题，内容及长度由引擎验证
     * @param expectedActorId 预期真实用户
     * @throws IllegalStateException 未授权、归属不符或存储失败
     */
    public void rename(AiChatMode mode, Long conversationId, String title, String expectedActorId) {
        requireId(conversationId);
        AiHostAction action = select(mode, AiHostAction.CHAT_RENAME, AiHostAction.GROUP_RENAME);
        managementService.renameForContext(mode, conversationId, title, authorize(expectedActorId, action));
    }

    /**
     * @param mode 聊天模式
     * @param conversationId 正数会话编号
     * @param pinned true 置顶，false 取消置顶
     * @param expectedActorId 预期真实用户
     * @throws IllegalStateException 未授权、归属不符或存储失败
     */
    public void pin(AiChatMode mode, Long conversationId, boolean pinned, String expectedActorId) {
        requireId(conversationId);
        AiHostAction action = select(mode, AiHostAction.CHAT_PIN, AiHostAction.GROUP_PIN);
        managementService.pinForContext(mode, conversationId, pinned, authorize(expectedActorId, action));
    }

    /**
     * @param mode 聊天模式
     * @param conversationId 正数会话编号；生成中允许删除，由引擎在提交后取消
     * @param expectedActorId 预期真实用户
     * @throws IllegalStateException 未授权、归属不符或删除失败；不返回伪成功
     */
    public void delete(AiChatMode mode, Long conversationId, String expectedActorId) {
        requireId(conversationId);
        AiHostAction action = select(mode, AiHostAction.CHAT_DELETE, AiHostAction.GROUP_DELETE);
        managementService.deleteForContext(mode, conversationId, authorize(expectedActorId, action));
    }

    /**
     * 更新群聊成员；引擎必须校验可信目录、拒绝生成中修改并清除旧模型会话。
     * @param conversationId 正数群聊会话编号
     * @param memberCodes 有序成员编码，数量采用统一常量，存在性及权限由真实目录校验
     * @param expectedActorId 预期真实用户
     * @throws IllegalArgumentException 成员数量或会话编号无效
     * @throws IllegalStateException 未授权、归属不符、生成中或成员不可用
     */
    public void updateGroupMembers(Long conversationId, List<String> memberCodes, String expectedActorId) {
        requireId(conversationId);
        if (memberCodes == null || memberCodes.size() < MIN_MEMBER_COUNT || memberCodes.size() > MAX_MEMBER_COUNT) {
            throw new IllegalArgumentException("群聊成员数量无效");
        }
        List<String> members = new ArrayList<>(memberCodes);
        managementService.updateGroupMembersForContext(conversationId, members,
                authorize(expectedActorId, AiHostAction.GROUP_MEMBERS_UPDATE));
    }

    /** 固定操作完成真实授权后形成身份匹配条件；引擎仍需重新捕获真实身份。 */
    private AiInvocationContext authorize(String expectedActorId, AiHostAction action) {
        AiHostSession session = authenticationBridge.authorizeCurrent(expectedActorId, action);
        if (session.getExpiresAtMillis() <= System.currentTimeMillis()) {
            throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED);
        }
        return new AiInvocationContext(session.getNamespace(), session.getTenantId(), session.getActorId(),
                UUID.randomUUID().toString());
    }

    /** 只在方法固定的两项权限间按模式分派，其他模式明确拒绝。 */
    private AiHostAction select(AiChatMode mode, AiHostAction single, AiHostAction group) {
        if (mode == AiChatMode.SINGLE) { return single; }
        if (mode == AiChatMode.GROUP) { return group; }
        throw new IllegalArgumentException("聊天模式无效");
    }

    /** 不允许缺失或非正数会话编号进入授权与存储链。 */
    private void requireId(Long id) {
        if (id == null || id <= 0) { throw new IllegalArgumentException("会话编号无效"); }
    }
}
