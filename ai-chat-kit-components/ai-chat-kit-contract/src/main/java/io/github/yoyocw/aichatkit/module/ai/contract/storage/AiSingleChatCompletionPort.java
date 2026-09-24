package io.github.yoyocw.aichatkit.module.ai.contract.storage;

/** 单聊完成原子提交：消息CAS与远端会话保存必须处于同一事务。 */
public interface AiSingleChatCompletionPort {
    /** @param command 本轮可信身份与完成结果，不包含认证凭据
     * @return CAS成功为true；消息已进入其它终态为false，不保存远端会话
     * @throws IllegalStateException 身份或消息归属无效；持久化异常向上传播并回滚 */
    boolean complete(AiSingleChatCompletionCommand command);
}
