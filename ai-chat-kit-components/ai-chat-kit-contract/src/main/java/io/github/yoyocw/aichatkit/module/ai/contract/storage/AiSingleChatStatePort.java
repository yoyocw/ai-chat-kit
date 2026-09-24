package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 单聊执行状态存储端口，不处理HTTP取消、审计和SSE。 */
public interface AiSingleChatStatePort {
    /** @return 当前用户助手消息的真实状态；不存在为MISSING，未知状态抛异常 */
    AiSingleChatState readState(AiInvocationContext context, Long messageId);
    /** 必须参与调用方停止事务；@return 明确的停止CAS结果，不提前提交 */
    AiSingleChatStopResult stop(AiInvocationContext context, Long messageId);
    /** 仅生成态写失败；CAS竞争后回读，数据库异常原样传播给响应收口。 */
    AiSingleChatState fail(AiSingleChatFailureCommand command);
    /** 归属校验后按当前user与turn清理远端会话；迟到调用零行不覆盖新绑定。 */
    void clearExpiredSession(AiInvocationContext context, Long conversationId, Long messageId);
}
