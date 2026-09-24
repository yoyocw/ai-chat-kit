package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

/** 对接宿主真实机器会话源及部署绑定；不得使用配置凭空制造机器身份。 */
public interface AiStopMachinePort {
    /**
     * @param bearer 真实机器Bearer凭据，仅调用栈使用
     * @param receiver 固定接收者和实体/scope/resource要求
     * @param role 固定原机器或消费者角色
     * @return 真实、未撤销、未过期的会话及当前绑定
     * @throws IllegalStateException 认证、真实实体、授权或来源绑定不符，禁止回退共享账号
     */
    AiStopMachineIdentity authenticate(String bearer, AiStopReceiver receiver, AiStopMachineRole role);

    /**
     * @param expected 已核验原机器快照，只用于查同一session及实体
     * @param receiver 固定接收者 @param role 固定认证角色
     * @return 同一个真实会话的当前状态和绑定，不能换另一个有效会话
     * @throws IllegalStateException 原会话失效、替换、撤权或源不可用
     */
    AiStopMachineIdentity recheck(AiStopMachineIdentity expected, AiStopReceiver receiver, AiStopMachineRole role);
}
