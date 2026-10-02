package io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/**
 * 显式身份 SQL 的内部执行作用域；不创建登录身份，不恢复宿主 ThreadLocal。
 * 仅适用于内置 JDBC/纯聊天异步组合，数据源路由和宿主拦截器不得依赖线程身份。
 * 配置选择该模式即声明此接入前提；依赖宿主线程上下文时必须提供自定义 scope。
 */
public final class AiJdbcExecutionScopeAdapter implements AiHostExecutionScopePort {
    /** 与状态存储共用的固定数据源和部署命名空间。 */
    private final AiJdbcAccess access;

    /** @param access 已验证同源 JDBC 事务管理器的访问对象 */
    public AiJdbcExecutionScopeAdapter(AiJdbcAccess access) { this.access = access; }

    /**
     * 校验同步捕获的可信上下文；隔离由每条 SQL 的显式身份条件承担。
     * 不在模型网络调用外围建立长事务，不执行实时撤权重验。
     * @param context 已授权内部上下文，不接受客户端任意声明
     * @param task 异步业务逻辑，异常原样传播
     */
    @Override
    public void execute(AiInvocationContext context, Runnable task) {
        access.scope(context);
        if (task == null) { throw new IllegalArgumentException("缺少异步执行逻辑"); }
        task.run();
    }

    /**
     * 独立只读 READ_COMMITTED 事务观察已提交状态，失败由引擎取消模型调用。
     * @param context 同步捕获的可信身份
     * @param task 仅限生成状态查询；两秒事务超时不覆盖连接池获取和全部网络耗时
     */
    @Override
    public void executeStateRead(AiInvocationContext context, Runnable task) {
        access.scope(context);
        access.readStateInNewTransaction(task);
    }
}
