package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionPort;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.ROLE_ASSISTANT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_COMPLETED;

/** 当前MyBatis宿主的单聊原子完成适配；沿用租户插件与app/turn条件。 */
@Service
@RequiredArgsConstructor
public class MyBatisSingleChatCompletionAdapter implements AiSingleChatCompletionPort {
    /** 消息归属检查与生成态条件更新。 */
    private final AiChatMessageMapper messageMapper;
    /** 本轮远端会话条件保存。 */
    private final AiChatConversationMapper conversationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean complete(AiSingleChatCompletionCommand command) {
        Long userId = validateContext(command);
        // 与发送、删除保持会话→消息锁顺序，避免新增事务形成反向等待。
        if (conversationMapper.selectByIdAndUserIdForUpdate(command.getConversationId(), userId) == null) {
            throw new IllegalStateException("完成提交的会话归属无效");
        }
        AiChatMessageDO current = messageMapper.selectByIdAndUserId(command.getMessageId(), userId);
        if (current == null || !Objects.equals(current.getConversationId(), command.getConversationId())
                || !ROLE_ASSISTANT.equals(current.getRole())) {
            throw new IllegalStateException("完成提交的消息归属无效");
        }
        // CAS决定唯一终态；失败时不得保存模型会话或覆盖停止状态。
        boolean completed = messageMapper.updateGeneratingMessage(AiChatMessageDO.builder()
                .id(command.getMessageId()).content(command.getContent()).status(STATUS_COMPLETED)
                .requestId(command.getRequestId()).responseData(command.getResponseData()).build());
        if (completed && StringUtils.hasText(command.getSessionId())) {
            // 保存异常向外传播，Spring事务回滚前面的CAS；零行仍沿用迟到绑定冲突语义。
            conversationMapper.saveBailianSession(command.getConversationId(), userId, command.getAppId(),
                    command.getMessageId(), command.getSessionId());
        }
        return completed;
    }

    /** 仅认可已建立的宿主租户作用域，DTO自身不作为身份证明；不输出载荷。 */
    private Long validateContext(AiSingleChatCompletionCommand command) {
        AiInvocationContext context = command == null ? null : command.getContext();
        if (context == null || !"platform".equals(context.getNamespace())
                || !StringUtils.hasText(context.getInvocationId()) || TenantContextHolder.isIgnore()
                || command.getConversationId() == null || command.getMessageId() == null
                || !StringUtils.hasText(command.getAppId())) {
            throw new IllegalStateException("完成提交的身份上下文无效");
        }
        try {
            Long userId = Long.valueOf(context.getActorId());
            Long tenantId = Long.valueOf(context.getTenantId());
            if (!Objects.equals(tenantId, TenantContextHolder.getTenantId())) {
                throw new IllegalStateException();
            }
            return userId;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("完成提交的宿主身份不匹配");
        }
    }
}
