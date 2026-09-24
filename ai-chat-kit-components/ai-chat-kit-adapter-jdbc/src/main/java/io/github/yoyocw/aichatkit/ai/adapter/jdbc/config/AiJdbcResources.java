package io.github.yoyocw.aichatkit.ai.adapter.jdbc.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

/** AI 持有的真实同源资源；本对象是 Bean，内部私有池和事务管理器不是宿主全局候选。 */
public final class AiJdbcResources implements AutoCloseable {
    /** 实际供 AI JDBC 使用的连接源。 */
    private final DataSource source;
    /** 与 source 同对象绑定的真实管理器。 */
    private final PlatformTransactionManager manager;
    /** 仅由 AI 创建的池，引用宿主资源时为空。 */
    private final HikariDataSource ownedPool;

    /** @param source 宿主已有源 @param manager 宿主明确选定的同源管理器；均不由本对象关闭 */
    public AiJdbcResources(DataSource source, PlatformTransactionManager manager) {
        this(source, manager, null);
    }

    /** 统一同源校验，不解包代理来猜测宿主路由语义。 */
    private AiJdbcResources(DataSource source, PlatformTransactionManager manager, HikariDataSource ownedPool) {
        if (source == null || !(manager instanceof DataSourceTransactionManager)
                || ((DataSourceTransactionManager) manager).getDataSource() != source) { throw invalid(); }
        this.source = source;
        this.manager = manager;
        this.ownedPool = ownedPool;
    }

    /**
     * @param properties 显式 isolated 配置，不加载宿主默认数据源属性
     * @return 真实且按首次数据库访问初始化的私有 Hikari 池及 DSTM，不注册全局 DataSource/PTM
     * @throws IllegalStateException 配置无效；不携带连接 URL、凭据或底层异常
     */
    public static AiJdbcResources isolated(AiJdbcResourceProperties properties) {
        validate(properties);
        HikariDataSource pool = new HikariDataSource();
        try {
            pool.setJdbcUrl(properties.getJdbcUrl());
            pool.setUsername(properties.getUsername());
            pool.setPassword(properties.getPassword());
            pool.setMaximumPoolSize(properties.getMaximumPoolSize());
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(properties.getConnectionTimeoutMs());
            pool.setValidationTimeout(properties.getValidationTimeoutMs());
            return new AiJdbcResources(pool, new DataSourceTransactionManager(pool), pool);
        } catch (RuntimeException ex) {
            pool.close();
            throw invalid();
        }
    }

    /** @return 实际 AI 连接源，仅用于显式接线，不能作为新登录或租户能力 */
    public DataSource source() { return source; }
    /** @return 唯一选定的真实 AI 事务管理器，与宿主默认选择无关 */
    public PlatformTransactionManager manager() { return manager; }

    /** 容器关闭时仅释放自有池，宿主引用源仍由原宿主管理。 */
    @Override
    public void close() { if (ownedPool != null) { ownedPool.close(); } }

    /** 校验显式连接边界，不试连或自动建表；运行连通性需独立验证。 */
    private static void validate(AiJdbcResourceProperties p) {
        if (p == null || p.getMode() != AiJdbcResourceMode.ISOLATED || p.getJdbcUrl() == null
                || !p.getJdbcUrl().startsWith("jdbc:postgresql:") || p.getJdbcUrl().length() > 4096
                || p.getUsername() == null || p.getUsername().trim().isEmpty() || p.getPassword() == null
                || p.getMaximumPoolSize() < 1 || p.getMaximumPoolSize() > 1000
                || p.getConnectionTimeoutMs() < 250 || p.getConnectionTimeoutMs() > 300000
                || p.getValidationTimeoutMs() < 250 || p.getValidationTimeoutMs() > p.getConnectionTimeoutMs()
                || p.getDataSourceBean() != null || p.getTransactionManagerBean() != null) { throw invalid(); }
    }

    /** @return 固定安全配置错误 */
    private static IllegalStateException invalid() { return new IllegalStateException("AI JDBC 资源配置无效或数据源与事务管理器不同源"); }
}
