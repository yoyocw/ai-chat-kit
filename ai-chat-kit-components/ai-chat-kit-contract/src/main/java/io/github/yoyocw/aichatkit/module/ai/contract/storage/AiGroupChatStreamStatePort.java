package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 群聊流执行存储边界；身份过滤、事务和条件更新由宿主承担，不依赖模型SDK。 */
public interface AiGroupChatStreamStatePort {
    /** @param context 可信宿主身份 @param messageId 助手占位ID
     * @return 0生成中、1完成、2停止、3失败；无授权记录为null */
    Integer readStatus(AiInvocationContext context, Long messageId);

    /** @param context 可信宿主身份 @param conversationId 会话ID @param messageId 当前轮次占位ID
     * 仅清理该用户该轮次的远端会话，数据库错误向外传播。 */
    void clearSession(AiInvocationContext context, Long conversationId, Long messageId);

    /** @param command 本轮完整回复和会话更新命令
     * @return 生成态CAS成功且所有回复事务提交为true，已结束或竞争失败为false
     * @throws IllegalStateException 身份/目标无效；持久化错误必须回滚并向外传播 */
    boolean complete(AiGroupChatCompletionCommand command);

    /** @param context 可信宿主身份 @param messageId 助手占位ID @param safeError 服务端安全错误文案
     * @return 仅从生成态改为失败时为true，竞争失败为false；错误不被适配器吞掉 */
    boolean fail(AiInvocationContext context, Long messageId, String safeError);
}
