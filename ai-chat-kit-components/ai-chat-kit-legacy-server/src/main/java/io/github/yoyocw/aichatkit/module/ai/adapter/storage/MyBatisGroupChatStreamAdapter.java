package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatCompletionCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatStreamStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupReplyRecord;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import org.springframework.util.StringUtils;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.*;

/** 芋道/MyBatis群聊流存储适配，保持生成态CAS先于成员插入和远端会话保存的顺序。 */
@Service
@RequiredArgsConstructor
public class MyBatisGroupChatStreamAdapter implements AiGroupChatStreamStatePort {
    /** 当前用户消息查询及条件更新。 */
    private final AiGroupChatMessageMapper messageMapper;
    /** 仅当前用户/轮次的远端会话绑定。 */
    private final AiGroupChatConversationMapper conversationMapper;
    /** CAS、成员回复及云端会话必须在已选定的同源事务中原子提交。 */
    private final AiTransactionExecutor transactions;

    @Override
    public Integer readStatus(AiInvocationContext context, Long messageId) {
        AiGroupChatMessageDO message = read(context, messageId);
        return message == null ? null : message.getStatus();
    }

    @Override
    public void clearSession(AiInvocationContext context, Long conversationId, Long messageId) {
        Long userId = validate(context);
        positive(conversationId);
        positive(messageId);
        conversationMapper.clearBailianSession(conversationId, userId, messageId);
    }

    @Override
    public boolean complete(AiGroupChatCompletionCommand command) {
        return transactions.required(() -> completeInTransaction(command));
    }

    /** 调用方已建立同源事务；任一步失败均回滚占位 CAS。 */
    private boolean completeInTransaction(AiGroupChatCompletionCommand command) {
        if (command == null || command.getReplies().isEmpty()) { throw invalid(); }
        Long userId = validate(command.getContext());
        positive(command.getConversationId());
        // 与授权停止统一会话→消息锁顺序，避免完成保存session与停止行锁交叉等待。
        if (conversationMapper.selectByIdAndUserIdForUpdate(command.getConversationId(), userId) == null) { return false; }
        AiGroupChatMessageDO current = read(command.getContext(), command.getMessageId());
        if (current == null || !Objects.equals(current.getConversationId(), command.getConversationId())) { return false; }
        // 占位成功完成后，成员插入或会话保存任何异常均回滚首条CAS。
        AiGroupReplyRecord first = command.getReplies().get(0);
        if (!messageMapper.updateGeneratingMessage(reply(command, first, userId, 1).id(command.getMessageId())
                .responseData(command.getResponseData()).build())) { return false; }
        for (int i = 1; i < command.getReplies().size(); i++) {
            messageMapper.insert(reply(command, command.getReplies().get(i), userId, i + 1).build());
        }
        if (StringUtils.hasText(command.getSessionId())) {
            conversationMapper.saveBailianSession(command.getConversationId(), userId, command.getAppId(),
                    command.getMessageId(), command.getSessionId());
        }
        return true;
    }

    @Override
    public boolean fail(AiInvocationContext context, Long messageId, String safeError) {
        if (read(context, messageId) == null) { return false; }
        return messageMapper.updateGeneratingMessage(AiGroupChatMessageDO.builder().id(messageId)
                .status(STATUS_FAILED).errorMessage(safeError).build());
    }

    /** 构造宿主记录；回复序号从一开始，首条与后续记录共享归属。 */
    private AiGroupChatMessageDO.AiGroupChatMessageDOBuilder reply(AiGroupChatCompletionCommand command,
            AiGroupReplyRecord reply, Long userId, int round) {
        if (reply == null || !StringUtils.hasText(reply.getSpeakerCode()) || reply.getContent() == null) { throw invalid(); }
        return AiGroupChatMessageDO.builder().conversationId(command.getConversationId()).userId(userId)
                .role(ROLE_ASSISTANT).speakerCode(reply.getSpeakerCode()).speakerName(reply.getSpeakerName())
                .roundNo(round).content(reply.getContent()).status(STATUS_COMPLETED).requestId(command.getRequestId());
    }

    /** 回读必须满足当前可信用户和助手角色，不将其它用户占位暴露给引擎。 */
    private AiGroupChatMessageDO read(AiInvocationContext context, Long messageId) {
        Long userId = validate(context);
        positive(messageId);
        AiGroupChatMessageDO value = messageMapper.selectByIdAndUserId(messageId, userId);
        return value != null && ROLE_ASSISTANT.equals(value.getRole()) ? value : null;
    }

    /** 显式约束宿主映射及租户过滤，不从命令切换身份或扩大数据权限。 */
    private Long validate(AiInvocationContext context) {
        if (context == null || !"platform".equals(context.getNamespace()) || !StringUtils.hasText(context.getInvocationId())
                || TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId() == null
                || !TenantContextHolder.getTenantId().toString().equals(context.getTenantId())
                || context.getActorId() == null || !context.getActorId().matches("[1-9][0-9]{0,18}")) { throw invalid(); }
        try { return Long.valueOf(context.getActorId()); } catch (NumberFormatException ex) { throw invalid(); }
    }

    /** 所有消息/会话主键必须是正数。 */
    private void positive(Long value) { if (value == null || value <= 0) { throw invalid(); } }

    /** 固定错误不附带用户内容或数据库异常上下文。 */
    private IllegalStateException invalid() { return new IllegalStateException("群聊存储上下文或目标无效"); }
}
