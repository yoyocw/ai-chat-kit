package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

/** 仅消费成功后调用的本地提交能力；必须使用真实数据库事务、锁与状态CAS。 */
public interface AiConsumedStopPort {
    /**
     * @param command 协调层构造的原来源/模式/消息/应用/期限，不作为新的认证凭据
     * 同一事务依次取得会话和助手锁、复查来源、校验原期限、CAS及审计，并在尾部复查期限。
     * 模型取消只允许afterCommit。过期、来源不符及数据库异常回滚；已消费票据不回放。
     */
    void commit(AiConsumedStopCommand command);
}
