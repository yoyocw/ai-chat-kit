package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationStorePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Objects;

/** 会话管理用例；每个操作在宿主授权后重新捕获完整身份，再访问 AI 自有存储。 */
@RequiredArgsConstructor
public class AiConversationManagementService {
    /** 真实认证上下文来源，不能由客户端任意构造。 */
    private final AiInvocationContextPort identities;
    /** 单群聊共用的管理存储能力。 */
    private final AiConversationStorePort storage;
    /** 仅删除事务提交后取消本实例仍在生成的请求。 */
    private final BailianClient client;
    /** 所选AI事务，删除提交后才取消模型调用。 */
    private final AiTransactionExecutor transactions;

    /** 已获独立创建权限后，在真实事务内创建空单聊会话，不生成假消息。 */
    public Long createSingleForContext(AiInvocationContext expectedContext) {
        return transactions.required(() -> {
            return storage.createSingle(capture(expectedContext));
        });
    }

    /** @param mode 固定聊天模式 @param expectedContext 宿主列表权限核验后的归属 @return 完整会话列表 */
    public List<AiConversationView> listConversationsForContext(AiChatMode mode, AiInvocationContext expectedContext) {
        return transactions.readOnly(() -> {
            return storage.list(capture(expectedContext), requiredMode(mode));
        });
    }

    /** @param mode 固定模式 @param conversationId 会话编号 @param expectedContext 已授权归属 @return 全部消息按编号升序 */
    public List<AiMessageView> listMessagesForContext(AiChatMode mode, Long conversationId, AiInvocationContext expectedContext) {
        return transactions.readOnly(() -> {
            return storage.messages(capture(expectedContext), requiredMode(mode), conversationId);
        });
    }

    /** @param mode 固定模式 @param conversationId 会话编号 @param title 新标题 @param expectedContext 已授权归属 */
    public void renameForContext(AiChatMode mode, Long conversationId, String title, AiInvocationContext expectedContext) {
        transactions.runRequired(() -> {
            storage.rename(capture(expectedContext), requiredMode(mode), conversationId, title);
        });
    }

    /** @param mode 固定模式 @param conversationId 会话编号 @param pinned 置顶意图 @param expectedContext 已授权归属 */
    public void pinForContext(AiChatMode mode, Long conversationId, boolean pinned, AiInvocationContext expectedContext) {
        transactions.runRequired(() -> {
            storage.pin(capture(expectedContext), requiredMode(mode), conversationId, pinned);
        });
    }

    /**
     * 删除生成中会话仍被允许；只在数据库提交后取消其模型调用，回滚不触发取消。
     * @param mode 固定模式
     * @param conversationId 会话编号
     * @param expectedContext 宿主删除权限核验后的归属
     */
    public void deleteForContext(AiChatMode mode, Long conversationId, AiInvocationContext expectedContext) {
        transactions.runRequired(() -> {
            transactions.requireActive();
            AiInvocationContext context = capture(expectedContext); AiChatMode actualMode = requiredMode(mode);
            List<Long> running = storage.delete(context, actualMode, conversationId);
            for (Long messageId : running) {
                transactions.afterCommit(() -> {
                    if (actualMode == AiChatMode.SINGLE) { client.cancel(messageId); }
                    else { client.cancelGroupWorkflow(messageId); }
                });
            }
        });
    }

    /** @param conversationId 群聊会话编号 @param codes 有序成员编码 @param expectedContext 成员更新权限核验后的归属 */
    public void updateGroupMembersForContext(Long conversationId, List<String> codes, AiInvocationContext expectedContext) {
        transactions.runRequired(() -> {
            storage.updateGroupMembers(capture(expectedContext), conversationId, codes);
        });
    }

    /** 重新捕获当前身份并比对三元归属；新的调用编号来自身份端口，不复用旧授权调用编号。 */
    private AiInvocationContext capture(AiInvocationContext expected) {
        if (expected == null) { throw new IllegalArgumentException("AI 会话管理授权上下文缺失"); }
        AiInvocationContext actual = identities.capture(expected.getActorId());
        if (actual == null || !Objects.equals(actual.getNamespace(), expected.getNamespace())
                || !Objects.equals(actual.getTenantId(), expected.getTenantId())
                || !Objects.equals(actual.getActorId(), expected.getActorId())) {
            throw new IllegalStateException("AI 授权前后身份不一致");
        }
        return actual;
    }

    /** 缺模式必须拒绝，不能扩大为跨模式操作。 */
    private AiChatMode requiredMode(AiChatMode mode) {
        if (mode == null) { throw new IllegalArgumentException("AI 聊天模式缺失"); }
        return mode;
    }
}
