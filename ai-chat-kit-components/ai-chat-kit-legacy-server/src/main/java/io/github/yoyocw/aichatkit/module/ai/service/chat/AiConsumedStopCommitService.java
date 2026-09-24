package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import org.springframework.util.StringUtils;
import java.util.Objects;
import java.util.Set;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.ROLE_ASSISTANT;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.CHAT_MESSAGE_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_MESSAGE_NOT_EXISTS;

/**
 * 消费成功后的内部本地提交边界，不是授权入口，不验证或签发任何票据。
 * 必须由协调层在锁事务外确认consume成功并建立原用户上下文后调用。
 */
@Service
@RequiredArgsConstructor
public class AiConsumedStopCommitService {
    /** 已核验的同源事务绑定，来源校验不得参与其它管理器的事务。 */
    private final AiTransactionExecutor transactions;
    /** 核对已建立的原用户身份，不以方法参数替代认证。 */
    private final AiInvocationContextPort contextPort;
    /** 来源复查通过代理加入本地事务。 */
    private final AiMessageOriginPort originPort;
    /** 单聊按用户查询助手。 */
    private final AiChatMessageMapper messageMapper;
    /** 单聊所属会话行锁，沿用租户过滤。 */
    private final AiChatConversationMapper conversationMapper;
    /** 群聊按用户查询助手占位。 */
    private final AiGroupChatMessageMapper groupMessageMapper;
    /** 群聊所属会话行锁，沿用租户过滤。 */
    private final AiGroupChatConversationMapper groupConversationMapper;
    /** 通过代理链复用单聊CAS、审计和本机取消。 */
    private final AiChatService chatService;
    /** 通过代理复用群聊事务、CAS、审计和本机取消。 */
    private final AiGroupChatService groupChatService;

    /**
     * 先锁会话，再复查助手及来源，最后在同一事务内调用既有停止实现。
     * @param context 已认证且已确认票据消费成功的本轮原用户上下文
     * @param caller 消费结果中的原业务调用方，不能传入AI消费者身份
     * @param mode 协调层已匹配票据的固定单聊/群聊模式
     * @param messageId 与已消费票据一致的正数助手消息ID
     * @param allowedAppIds 消费结果中的当前应用白名单，不允许请求自报
     * @param authorizationExpiresAtMillis 已消费票据与原两会话取最小值的截止时刻，UTC Unix 毫秒，不得重新计算续期
     * @throws IllegalStateException 上下文或来源不匹配，或授权期限缺失、非正、已到期
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 目标不存在或不在生成中
     * 数据库异常或期限校验失败回滚本地事务；既有取消操作仅在提交后尽力执行，不回放票据。
     */
    public void commit(AiInvocationContext context, AiCallerOrigin caller, AiChatMode mode,
                       Long messageId, Set<String> allowedAppIds, Long authorizationExpiresAtMillis) {
        transactions.runRequired(() -> {
            requireUnexpired(authorizationExpiresAtMillis);
            validateContext(context, caller, mode, messageId);
            if (mode == AiChatMode.SINGLE) {
                lockSingleConversation(messageId, caller.getUserId());
            } else {
                lockGroupConversation(messageId, caller.getUserId());
            }
            // 无远程认证调用；来源与后续状态CAS处于相同本地事务。
            originPort.verify(context, caller, mode, messageId, allowedAppIds);
            // 实际会话及助手消息锁已经取得，来源复查亦可能耗时；CAS前必须仍在原授权窗口。
            requireUnexpired(authorizationExpiresAtMillis);
            if (mode == AiChatMode.SINGLE) {
                chatService.stopMessage(messageId, caller.getUserId());
            } else {
                groupChatService.stopMessage(messageId, caller.getUserId());
            }
            // 既有停止内部捕获身份、SQL及审计也可能耗时；跨期则回滚，提交后取消回调不会运行。
            requireUnexpired(authorizationExpiresAtMillis);
        });
    }

    /** 缺失期限不能采用默认值，等于当前时刻即失效；不以当前时间重算新的授权窗口。 */
    private void requireUnexpired(Long authorizationExpiresAtMillis) {
        if (authorizationExpiresAtMillis == null || authorizationExpiresAtMillis <= 0
                || authorizationExpiresAtMillis <= System.currentTimeMillis()) {
            throw new IllegalStateException("停止授权已失效或期限无效");
        }
    }

    /** 无锁读取仅定位会话；会话锁定后重读，不能沿用锁前消息判断。 */
    private void lockSingleConversation(Long messageId, Long userId) {
        AiChatMessageDO before = messageMapper.selectByIdAndUserId(messageId, userId);
        if (before == null || !ROLE_ASSISTANT.equals(before.getRole()) || before.getConversationId() == null
                || before.getConversationId() <= 0 || !Objects.equals(before.getUserId(), userId)) {
            throw exception(CHAT_MESSAGE_NOT_EXISTS);
        }
        Long conversationId = before.getConversationId();
        if (conversationMapper.selectByIdAndUserIdForUpdate(conversationId, userId) == null) {
            throw exception(CHAT_MESSAGE_NOT_EXISTS);
        }
        AiChatMessageDO current = messageMapper.selectStopTargetForUpdate(
                TenantContextHolder.getTenantId(), messageId, userId);
        if (current == null || !ROLE_ASSISTANT.equals(current.getRole())
                || !Objects.equals(current.getUserId(), userId)
                || !Objects.equals(current.getConversationId(), conversationId)) {
            throw exception(CHAT_MESSAGE_NOT_EXISTS);
        }
    }

    /** 群聊保持同样的会话先于消息CAS顺序，不修改旧停止入口锁行为。 */
    private void lockGroupConversation(Long messageId, Long userId) {
        AiGroupChatMessageDO before = groupMessageMapper.selectByIdAndUserId(messageId, userId);
        if (before == null || !ROLE_ASSISTANT.equals(before.getRole()) || before.getConversationId() == null
                || before.getConversationId() <= 0 || !Objects.equals(before.getUserId(), userId)) {
            throw exception(GROUP_MESSAGE_NOT_EXISTS);
        }
        Long conversationId = before.getConversationId();
        if (groupConversationMapper.selectByIdAndUserIdForUpdate(conversationId, userId) == null) {
            throw exception(GROUP_MESSAGE_NOT_EXISTS);
        }
        AiGroupChatMessageDO current = groupMessageMapper.selectStopTargetForUpdate(
                TenantContextHolder.getTenantId(), messageId, userId);
        if (current == null || !ROLE_ASSISTANT.equals(current.getRole())
                || !Objects.equals(current.getUserId(), userId)
                || !Objects.equals(current.getConversationId(), conversationId)) {
            throw exception(GROUP_MESSAGE_NOT_EXISTS);
        }
    }

    /** 查询前核对可信租户及用户，拒绝忽略租户过滤；不改变上下文或扩大权限。 */
    private void validateContext(AiInvocationContext context, AiCallerOrigin caller,
                                 AiChatMode mode, Long messageId) {
        if (context == null || caller == null || mode == null || messageId == null || messageId <= 0
                || !"platform".equals(context.getNamespace()) || !StringUtils.hasText(context.getInvocationId())
                || TenantContextHolder.isIgnore() || !Objects.equals(caller.getTenantId(), TenantContextHolder.getTenantId())
                || !caller.getTenantId().toString().equals(context.getTenantId())
                || !caller.getUserId().toString().equals(context.getActorId())) {
            throw new IllegalStateException("停止提交上下文不匹配");
        }
        AiInvocationContext current = contextPort.capture(context.getActorId());
        if (current == null || !Objects.equals(current.getNamespace(), context.getNamespace())
                || !Objects.equals(current.getTenantId(), context.getTenantId())
                || !Objects.equals(current.getActorId(), context.getActorId())) {
            throw new IllegalStateException("停止提交身份不匹配");
        }
    }
}
