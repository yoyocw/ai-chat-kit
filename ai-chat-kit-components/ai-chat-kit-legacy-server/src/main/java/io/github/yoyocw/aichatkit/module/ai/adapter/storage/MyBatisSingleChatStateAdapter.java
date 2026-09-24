package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatState;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStopResult;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatFailureCommand;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.*;

/** 复用既有归属查询与单条CAS；失败回读不新增事务快照或锁顺序。 */
@Service
@RequiredArgsConstructor
public class MyBatisSingleChatStateAdapter implements AiSingleChatStatePort {
    /** 助手消息归属与条件更新。 */
    private final AiChatMessageMapper messageMapper;
    /** 当前轮次远端会话清理。 */
    private final AiChatConversationMapper conversationMapper;

    @Override
    public AiSingleChatState readState(AiInvocationContext context, Long messageId) {
        return state(readAssistant(messageId, validateContext(context)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AiSingleChatStopResult stop(AiInvocationContext context, Long messageId) {
        AiSingleChatState current = state(readAssistant(messageId, validateContext(context)));
        if (current == AiSingleChatState.MISSING) {
            return AiSingleChatStopResult.NOT_FOUND;
        }
        if (current != AiSingleChatState.GENERATING) {
            return AiSingleChatStopResult.NOT_GENERATING;
        }
        return messageMapper.updateGeneratingMessage(AiChatMessageDO.builder().id(messageId)
                .status(STATUS_STOPPED).build())
                ? AiSingleChatStopResult.STOPPED : AiSingleChatStopResult.NOT_GENERATING;
    }

    @Override
    public AiSingleChatState fail(AiSingleChatFailureCommand command) {
        if (command == null) {
            throw new IllegalStateException("缺少失败提交参数");
        }
        Long userId = validateContext(command.getContext());
        AiSingleChatState current = state(readAssistant(command.getMessageId(), userId));
        if (current != AiSingleChatState.GENERATING) {
            return current;
        }
        if (messageMapper.updateGeneratingMessage(AiChatMessageDO.builder().id(command.getMessageId())
                .content(command.getPartialContent()).status(STATUS_FAILED)
                .errorMessage(command.getErrorMessage()).build())) {
            return AiSingleChatState.FAILED;
        }
        // 不把竞争失败当作本次成功，沿用真实状态回读；异常向上交给SSE finally。
        return state(readAssistant(command.getMessageId(), userId));
    }

    @Override
    public void clearExpiredSession(AiInvocationContext context, Long conversationId, Long messageId) {
        Long userId = validateContext(context);
        AiChatMessageDO message = readAssistant(messageId, userId);
        if (conversationId == null || message == null
                || !Objects.equals(conversationId, message.getConversationId())
                || conversationMapper.selectByIdAndUserId(conversationId, userId) == null) {
            throw new IllegalStateException("失效会话清理归属无效");
        }
        conversationMapper.clearBailianSession(conversationId, userId, messageId);
    }

    /** 角色和归属均匹配才返回助手消息，不泄露其他用户记录。 */
    private AiChatMessageDO readAssistant(Long messageId, Long userId) {
        if (messageId == null) {
            return null;
        }
        AiChatMessageDO message = messageMapper.selectByIdAndUserId(messageId, userId);
        return message != null && ROLE_ASSISTANT.equals(message.getRole()) ? message : null;
    }

    /** 映射全部现有状态；未知持久化值不得冒充成功。 */
    private AiSingleChatState state(AiChatMessageDO message) {
        if (message == null) { return AiSingleChatState.MISSING; }
        Integer value = message.getStatus();
        if (Objects.equals(value, STATUS_GENERATING)) { return AiSingleChatState.GENERATING; }
        if (Objects.equals(value, STATUS_COMPLETED)) { return AiSingleChatState.COMPLETED; }
        if (Objects.equals(value, STATUS_STOPPED)) { return AiSingleChatState.STOPPED; }
        if (Objects.equals(value, STATUS_FAILED)) { return AiSingleChatState.FAILED; }
        throw new IllegalStateException("未知单聊消息状态");
    }

    /** 核对已建立的宿主作用域，原始认证由同步入口完成。 */
    private Long validateContext(AiInvocationContext context) {
        if (context == null || !"platform".equals(context.getNamespace())
                || !StringUtils.hasText(context.getInvocationId()) || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("单聊状态上下文无效");
        }
        try {
            Long userId = Long.valueOf(context.getActorId());
            if (!Objects.equals(Long.valueOf(context.getTenantId()), TenantContextHolder.getTenantId())) {
                throw new IllegalStateException();
            }
            return userId;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("单聊状态宿主身份不匹配");
        }
    }
}
