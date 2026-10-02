package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiMybatisSession;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.mapper.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/** 固定数据源、事务管理器及部署作用域，拒绝在错误数据源事务内写消息。 */
public final class AiJdbcAccess {
    /** 部署固定命名空间。 */
    private final String namespace;
    /** AI 私有 MyBatis-Plus 会话与 Mapper。 */
    private final AiMybatisSession sessions;
    /** 与所选数据源对应的真实数据库事务。 */
    private final AiTransactionExecutor executor;

    /** @throws IllegalStateException 事务管理器与数据源不对应或部署未声明命名空间 */
    public AiJdbcAccess(DataSource dataSource, PlatformTransactionManager manager, String namespace) {
        this(dataSource, manager, namespace, new AiMybatisSession(dataSource));
    }

    private AiJdbcAccess(DataSource dataSource, PlatformTransactionManager manager, String namespace, AiMybatisSession sessions) {
        this(dataSource, sessions.executor(manager, AiTransactionMode.REUSE_HOST), namespace, sessions);
    }

    /** @param dataSource AI 选定源 @param executor 同源执行器 @param namespace 固定命名空间；不依赖宿主默认管理器 */
    public AiJdbcAccess(DataSource dataSource, AiTransactionExecutor executor, String namespace) {
        this(dataSource, executor, namespace, new AiMybatisSession(dataSource));
    }

    public AiJdbcAccess(DataSource dataSource, AiTransactionExecutor executor, String namespace, AiMybatisSession sessions) {
        if (dataSource == null || executor == null || sessions == null || !sessions.usesSource(dataSource) || !(executor.manager() instanceof DataSourceTransactionManager)
                || ((DataSourceTransactionManager) executor.manager()).getDataSource() != dataSource
                || executor.resourceFactory() != dataSource
                || namespace == null || namespace.trim().isEmpty() || namespace.length() > 128) {
            throw new IllegalStateException("AI PostgreSQL 适配需要同一数据源的 JDBC 事务管理器及有效命名空间");
        }
        this.namespace = namespace;
        this.sessions = sessions; this.executor = executor;
    }

    /** @return 校验部署归属后的参数绑定作用域 */
    public AiJdbcScope scope(AiInvocationContext context) { return new AiJdbcScope(context, namespace); }
    /** @return 启动时冻结的部署命名空间，公开分享查询不能由访客参数覆盖 */
    public String namespace() { return namespace; }
    public AiConversationMapper conversations() { return sessions.mapper(AiConversationMapper.class); }
    public AiMessageMapper messages() { return sessions.mapper(AiMessageMapper.class); }
    public AiMemberMapper members() { return sessions.mapper(AiMemberMapper.class); }
    public AiOriginMapper origins() { return sessions.mapper(AiOriginMapper.class); }
    public AiExecutionMapper executions() { return sessions.mapper(AiExecutionMapper.class); }
    /** @return 同源显式事务执行器，外部事务处理和提交回调归属由它统一检查 */
    public AiTransactionExecutor executor() { return executor; }
    /**
     * 使用同源独立已提交快照查询生成状态；两秒为事务/语句超时，非连接获取及网络硬截止。
     * @param task 仅执行状态读取，不得写入或调用外部服务
     */
    public void readStateInNewTransaction(Runnable task) {
        if (task == null) { throw new IllegalArgumentException("缺少生成状态查询"); }
        executor.requiresNewReadOnly(() -> { task.run(); return null; }, 2);
    }
    /** 占位、来源及审计必须加入外层发送事务，禁止自行提前提交。 */
    public void requireTransaction() {
        executor.requireActive();
    }
}
