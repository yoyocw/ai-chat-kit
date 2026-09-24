package io.github.yoyocw.aichatkit.module.ai.contract.storage;

/** 单聊发送准备存储步骤，必须参与调用方已有事务，不能独立提前提交。 */
public interface AiSingleChatPreparePort {
    /** @param command 本轮已授权准备参数
     * @return 本轮占位及远端上下文快照；返回不代表事务已提交
     * @throws RuntimeException 无事务、归属无效、已有生成任务或持久化失败
     * 调用方必须在同一事务执行后续业务查询和审计，失败时整体回滚。 */
    AiSingleChatPreparedTurn prepare(AiSingleChatPrepareCommand command);
}
