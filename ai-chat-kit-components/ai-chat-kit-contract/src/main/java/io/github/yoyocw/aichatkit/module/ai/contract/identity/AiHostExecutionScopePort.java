package io.github.yoyocw.aichatkit.module.ai.contract.identity;

/** 受信内部异步调用作用域；通过显式 SQL 身份或宿主上下文维持可信隔离，不授予新的登录身份。 */
public interface AiHostExecutionScopePort {
    /** @param context 同步捕获并验证可映射的上下文，不能来自HTTP反序列化
     * @param task 当前调用的执行逻辑，异常原样传播
     * 若修改宿主线程上下文，必须在 finally 中恢复原值；显式身份实现无需构造线程身份。
     * 不保证实时撤权重验。 */
    void execute(AiInvocationContext context, Runnable task);

    /**
     * 在可信隔离作用域中读取已提交生成状态，供跨节点停止监测使用。
     * 宿主实现必须采用独立只读事务并限制查询时长，不能复用长事务中的旧快照。
     * @param context 同步捕获的可信调用身份，不接受客户端任意声明
     * @param task 仅执行生成状态查询，不写入业务数据
     * @throws RuntimeException 未实现该能力、上下文无效或查询失败；引擎将取消当前模型调用
     */
    default void executeStateRead(AiInvocationContext context, Runnable task) {
        throw new IllegalStateException("宿主未提供有界生成状态查询能力");
    }
}
