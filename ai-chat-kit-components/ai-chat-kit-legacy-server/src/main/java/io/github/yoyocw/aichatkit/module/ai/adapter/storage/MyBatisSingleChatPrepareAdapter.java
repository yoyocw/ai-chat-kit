package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPrepareCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatConversationDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryMessage;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryResult;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.*;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiModelExecutionConstants.ERROR_CODE_STALE_TIMEOUT;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.CHAT_CONVERSATION_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.CHAT_MESSAGE_GENERATING;

/** MyBatis发送准备适配，只加入外层事务；地图/审计失败仍回滚本轮全部写入。 */
@Service
@RequiredArgsConstructor
public class MyBatisSingleChatPrepareAdapter implements AiSingleChatPreparePort {
    /** 会话归属查询、行锁及应用轮次绑定。 */
    private final AiChatConversationMapper conversationMapper;
    /** 历史读取与本轮消息占位。 */
    private final AiChatMessageMapper messageMapper;
    /** 保留失联审计更新与消息更新的原顺序。 */
    private final AiExecutionAuditPort executionAuditService;
    /** 复用现有纯记忆计算，不修改预算或摘要算法。 */
    private final AiConversationMemoryService memoryService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public AiSingleChatPreparedTurn prepare(AiSingleChatPrepareCommand command) {
        Long userId = validateCommand(command);
        AiChatConversationDO conversation = getOrCreateConversation(command, userId);
        // 保持原行为：取得会话锁后计算一次截止时间，消息与审计使用同值。
        LocalDateTime expiredBefore = LocalDateTime.now().minusSeconds(command.getStaleTimeoutSeconds());
        messageMapper.failStaleGenerating(conversation.getId(), userId, expiredBefore);
        executionAuditService.failStale(command.getContext(), AiChatMode.SINGLE, conversation.getId(), expiredBefore,
                ERROR_CODE_STALE_TIMEOUT);
        if (messageMapper.selectGeneratingCount(conversation.getId(), userId) > 0) {
            throw exception(CHAT_MESSAGE_GENERATING);
        }
        // 摘要游标与占位同事务；历史不能包含本轮刚发送的用户消息。
        String historySummary = buildHistorySummary(conversation.getId(), userId);
        insertUserMessage(conversation.getId(), userId, command);
        AiChatMessageDO assistant = insertAssistantMessage(conversation.getId(), userId, command);
        boolean appChanged = bindBailianTurn(conversation, command.getAppId(), assistant.getId(), userId);
        updateConversationAfterSend(conversation, command);
        return new AiSingleChatPreparedTurn(conversation.getId(), assistant.getId(),
                conversation.getBailianSessionId(), appChanged, historySummary);
    }

    /** 显式身份必须匹配已有租户作用域；不把命令当作认证证明。 */
    private Long validateCommand(AiSingleChatPrepareCommand command) {
        AiInvocationContext context = command == null ? null : command.getContext();
        if (context == null || !"platform".equals(context.getNamespace()) || TenantContextHolder.isIgnore()
                || !StringUtils.hasText(context.getInvocationId()) || !StringUtils.hasText(command.getContent())
                || !StringUtils.hasText(command.getAppId()) || command.getStaleTimeoutSeconds() <= 30L
                || command.getStaleTimeoutSeconds() > Integer.MAX_VALUE + 30L) {
            throw new IllegalStateException("发送准备上下文无效");
        }
        try {
            Long userId = Long.valueOf(context.getActorId());
            if (!Objects.equals(Long.valueOf(context.getTenantId()), TenantContextHolder.getTenantId())) {
                throw new IllegalStateException();
            }
            return userId;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("发送准备宿主身份不匹配");
        }
    }

    /** 行锁内绑定本轮应用；未知来源的旧会话也重建，本地消息与摘要继续保留。 */
    private boolean bindBailianTurn(AiChatConversationDO conversation, String appId, Long turnId, Long userId) {
        boolean reset = !Objects.equals(appId, conversation.getBailianAppId());
        boolean notifyChange = reset && (StringUtils.hasText(conversation.getBailianAppId())
                || StringUtils.hasText(conversation.getBailianSessionId()));
        conversationMapper.bindBailianTurn(conversation.getId(), userId, appId, turnId, reset);
        if (reset) {
            conversation.setBailianSessionId(null);
        }
        conversation.setBailianAppId(appId);
        conversation.setBailianTurnId(turnId);
        return notifyChange;
    }

    /** 锁定已有会话或为首次提问创建新会话。 */
    private AiChatConversationDO getOrCreateConversation(AiSingleChatPrepareCommand reqVO, Long userId) {
        if (reqVO.getConversationId() == null) {
            AiChatConversationDO conversation = AiChatConversationDO.builder()
                    .userId(userId).title(buildAutoTitle(reqVO.getContent())).build();
            conversationMapper.insert(conversation);
            return conversation;
        }
        AiChatConversationDO conversation = conversationMapper.selectByIdAndUserIdForUpdate(
                reqVO.getConversationId(), userId);
        if (conversation == null) {
            throw exception(CHAT_CONVERSATION_NOT_EXISTS);
        }
        return conversation;
    }

    /** 写入用户消息，作为本轮生成的稳定输入事实。 */
    private void insertUserMessage(Long conversationId, Long userId, AiSingleChatPrepareCommand reqVO) {
        messageMapper.insert(AiChatMessageDO.builder().conversationId(conversationId).userId(userId)
                .role(ROLE_USER).content(reqVO.getContent().trim())
                .mapEnabled(reqVO.isMapEnabled()).status(STATUS_COMPLETED).build());
    }

    /** 写入助手生成态占位消息，作为并发控制和停止生成的本地依据。 */
    private AiChatMessageDO insertAssistantMessage(Long conversationId, Long userId, AiSingleChatPrepareCommand reqVO) {
        AiChatMessageDO message = AiChatMessageDO.builder().conversationId(conversationId).userId(userId)
                .role(ROLE_ASSISTANT).content("")
                .mapEnabled(reqVO.isMapEnabled()).status(STATUS_GENERATING).build();
        messageMapper.insert(message);
        return message;
    }

    /** 首轮发送时更新自动标题，并触发会话更新时间维护。 */
    private void updateConversationAfterSend(AiChatConversationDO conversation, AiSingleChatPrepareCommand reqVO) {
        String title = DEFAULT_TITLE.equals(conversation.getTitle()) ? buildAutoTitle(reqVO.getContent()) : null;
        conversationMapper.updateById(AiChatConversationDO.builder().id(conversation.getId()).title(title).build());
    }

    /**
     * 从当前用户的已完成消息生成有限历史摘要，供百炼跨越短期云端会话理解近期上下文。
     *
     * @param conversationId 会话编号
     * @param userId 登录用户编号
     * @return 滚动摘要与最近十二条原文组成的 Token 预算上下文；新会话返回空字符串
     */
    private String buildHistorySummary(Long conversationId, Long userId) {
        AiChatConversationDO conversation = conversationMapper.selectByIdAndUserId(conversationId, userId);
        List<AiConversationMemoryMessage> messages = new ArrayList<AiConversationMemoryMessage>();
        for (AiChatMessageDO message : messageMapper.selectListByConversationId(conversationId, userId)) {
            if (message.getStatus() != STATUS_COMPLETED || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String speaker = ROLE_USER.equals(message.getRole()) ? "用户" : "助手";
            messages.add(new AiConversationMemoryMessage(message.getId(), speaker, message.getContent()));
        }
        AiConversationMemoryResult result = memoryService.build(conversation.getMemorySummary(),
                conversation.getMemoryCursorMessageId(), messages);
        if (result.isChanged()) {
            conversationMapper.updateById(AiChatConversationDO.builder().id(conversationId)
                    .memorySummary(result.getSummary()).memoryCursorMessageId(result.getCursorMessageId()).build());
        }
        return result.getContext();
    }

    /** 按 Unicode 码点截取首次提问生成的默认会话标题。 */
    private String buildAutoTitle(String content) {
        String value = content.trim();
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= AUTO_TITLE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, AUTO_TITLE_MAX_LENGTH)) + "…";
    }

}
