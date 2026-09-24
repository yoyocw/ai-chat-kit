package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 宿主业务事实准备端口；授权失败必须抛出，不得伪装成空结果。 */
public interface AiBusinessContextPort {
    /**
     * 准备同一轮问题的授权事实及可选展示数据，不能包含工具凭据。
     * @param request 由认证入口桥接的身份及服务端选择的能力；对象本身不是认证证明
     * @return 同一次查询产生的不可变快照
     */
    AiBusinessSnapshot prepare(AiBusinessContextRequest request);
}
