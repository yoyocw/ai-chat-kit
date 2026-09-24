package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStopResult;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatConversationDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryMessage;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryResult;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.*;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.AUTO_TITLE_MAX_LENGTH;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiModelExecutionConstants.ERROR_CODE_STALE_TIMEOUT;

/** 原林业群聊表的同步准备端口；只参与外层事务，不调用模型或回调旧 Service。 */
@Service
@RequiredArgsConstructor
public class MyBatisGroupChatPrepareAdapter implements AiGroupChatPreparePort {
    /** 与来源、审计及引擎相同的实际事务资源。 */
    private final AiTransactionExecutor transactions;
    /** 原会话、应用绑定及记忆游标。 */
    private final AiGroupChatConversationMapper conversationMapper;
    /** 原历史、占位及生成态 CAS。 */
    private final AiGroupChatMessageMapper messageMapper;
    /** 保留成员规范化、目录顺序及原错误码。 */
    private final MyBatisGroupChatSupport support;
    /** 与失联消息使用同一截止时间更新原审计。 */
    private final AiExecutionAuditPort executionAuditService;
    /** 沿用原滚动摘要算法与预算。 */
    private final AiConversationMemoryService memoryService;

    /** 在外层事务中创建原表会话及成员；不增加应用授权或签发行为。 */
    @Override
    public Long create(AiInvocationContext context, String title, List<String> requestedCodes) {
        return transactions.mandatory(() -> {
            Long userId = validate(context);
            List<String> memberCodes = support.validateMembers(requestedCodes);
            String actualTitle = StringUtils.hasText(title) ? title.trim() : support.buildDefaultTitle(memberCodes);
            AiGroupChatConversationDO conversation = AiGroupChatConversationDO.builder()
                    .userId(userId).title(actualTitle).build();
            conversationMapper.insert(conversation);
            support.replaceMembers(conversation.getId(), userId, memberCodes);
            return conversation.getId();
        });
    }

    /** 锁、失联清理、成员、记忆与占位属于同一外层事务，后续来源失败时一起回滚。 */
    @Override
    public AiGroupChatPreparedTurn prepare(AiInvocationContext context, Long conversationId, String content,
                                          String appId, long staleTimeoutSeconds) {
        return transactions.mandatory(() -> {
            Long userId = validate(context);
            if (conversationId == null || conversationId <= 0 || !StringUtils.hasText(content)
                    || content.length() > 10000 || !StringUtils.hasText(appId)
                    || staleTimeoutSeconds <= 30 || staleTimeoutSeconds > Integer.MAX_VALUE + 30L) {
                throw new IllegalStateException("群聊准备参数无效");
            }
            AiGroupChatConversationDO conversation = support.validateConversationForUpdate(conversationId, userId);
            LocalDateTime expiredBefore = LocalDateTime.now().minusSeconds(staleTimeoutSeconds);
            messageMapper.failStaleGenerating(conversationId, userId, expiredBefore);
            executionAuditService.failStale(context, AiChatMode.GROUP, conversationId, expiredBefore, ERROR_CODE_STALE_TIMEOUT);
            support.ensureNotGenerating(conversationId, userId);
            List<AiGroupMemberSnapshot> members = support.loadEnabledAgents(support.loadMemberCodes(conversationId, userId));
            String history = buildHistorySummary(conversation, userId);
            updateDefaultTitleFromFirstMessage(conversation, members, history, content);
            insertUserMessage(conversationId, userId, content);
            AiGroupChatMessageDO placeholder = insertPlaceholder(conversationId, userId);
            boolean appChanged = bindBailianTurn(conversation, userId, appId, placeholder.getId());
            support.touchConversation(conversationId);
            return new AiGroupChatPreparedTurn(conversationId, placeholder.getId(),
                    conversation.getBailianSessionId(), history, appChanged, members);
        });
    }

    /** 保留原助手归属及生成态 CAS；审计与提交后取消由外层引擎统一处理。 */
    @Override
    public AiSingleChatStopResult stop(AiInvocationContext context, Long messageId) {
        return transactions.mandatory(() -> {
            Long userId = validate(context);
            if (messageId == null || messageId <= 0) { throw new IllegalStateException("群聊消息编号无效"); }
            AiGroupChatMessageDO message = messageMapper.selectByIdAndUserId(messageId, userId);
            if (message == null || !ROLE_ASSISTANT.equals(message.getRole())) { return AiSingleChatStopResult.NOT_FOUND; }
            if (message.getStatus() != STATUS_GENERATING || !messageMapper.updateGeneratingMessage(
                    AiGroupChatMessageDO.builder().id(messageId).status(STATUS_STOPPED).build())) {
                return AiSingleChatStopResult.NOT_GENERATING;
            }
            return AiSingleChatStopResult.STOPPED;
        });
    }

    /** 参数只能匹配已经建立的宿主租户作用域，不能据此切换租户或扩大用户身份。 */
    private Long validate(AiInvocationContext context) {
        if (context == null || !"platform".equals(context.getNamespace()) || !StringUtils.hasText(context.getInvocationId())
                || TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId() == null
                || !TenantContextHolder.getTenantId().toString().equals(context.getTenantId())
                || context.getActorId() == null || !context.getActorId().matches("[1-9][0-9]{0,18}")) {
            throw new IllegalStateException("群聊准备宿主身份不匹配");
        }
        try { return Long.valueOf(context.getActorId()); }
        catch (NumberFormatException ex) { throw new IllegalStateException("群聊准备宿主身份不匹配"); }
    }
    /** 先写入本轮用户消息，保持已完成状态及原历史顺序。 */
    private void insertUserMessage(Long conversationId, Long userId, String content) {
        messageMapper.insert(AiGroupChatMessageDO.builder().conversationId(conversationId).userId(userId)
                .role(ROLE_USER).content(content).status(STATUS_COMPLETED).build());
    }

    /** 插入空助手占位，后续来源或审计失败时随外层事务回滚。 */
    private AiGroupChatMessageDO insertPlaceholder(Long conversationId, Long userId) {
        AiGroupChatMessageDO message = AiGroupChatMessageDO.builder().conversationId(conversationId).userId(userId)
                .role(ROLE_ASSISTANT).content("").status(STATUS_GENERATING).build();
        messageMapper.insert(message);
        return message;
    }

    /** 应用变化时清空云端会话；只有存在旧应用或会话时通知重建，每轮绑定占位编号。 */
    private boolean bindBailianTurn(AiGroupChatConversationDO conversation, Long userId, String appId, Long turnId) {
        boolean reset = !java.util.Objects.equals(appId, conversation.getBailianAppId());
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

    /** 仅使用已完成且有正文的历史，摘要及游标与本轮占位同事务推进。 */
    private String buildHistorySummary(AiGroupChatConversationDO conversation, Long userId) {
        List<AiConversationMemoryMessage> messages = new ArrayList<AiConversationMemoryMessage>();
        for (AiGroupChatMessageDO message : messageMapper.selectListByConversationId(conversation.getId(), userId)) {
            if (message.getStatus() != STATUS_COMPLETED || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String speaker = ROLE_USER.equals(message.getRole()) ? "用户" : message.getSpeakerName();
            messages.add(new AiConversationMemoryMessage(message.getId(), speaker, message.getContent()));
        }
        AiConversationMemoryResult result = memoryService.build(conversation.getMemorySummary(),
                conversation.getMemoryCursorMessageId(), messages);
        if (result.isChanged()) {
            conversationMapper.updateById(AiGroupChatConversationDO.builder().id(conversation.getId())
                    .memorySummary(result.getSummary()).memoryCursorMessageId(result.getCursorMessageId()).build());
        }
        return result.getContext();
    }

    /** 无历史且标题仍为默认成员组合时才自动改名，保留用户自定义标题。 */
    private void updateDefaultTitleFromFirstMessage(AiGroupChatConversationDO conversation,
                                                    List<AiGroupMemberSnapshot> members,
                                                    String historySummary, String prompt) {
        if (StringUtils.hasText(historySummary) || !support.buildDefaultTitleByAgents(members).equals(conversation.getTitle())) {
            return;
        }
        String title = truncate(prompt, AUTO_TITLE_MAX_LENGTH);
        conversationMapper.updateById(AiGroupChatConversationDO.builder().id(conversation.getId()).title(title).build());
        conversation.setTitle(title);
    }

    /** 保留旧标题截取行为：超长时取末尾 maxLength 个字符。 */
    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(value.length() - maxLength);
    }
}
