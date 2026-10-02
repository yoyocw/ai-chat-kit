package io.github.yoyocw.aichatkit.ai.adapter.jdbc.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.mapper.*;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.Collections;

/** AI 私有会话工厂；不参与宿主 Mapper 扫描，也不暴露全局 SqlSessionFactory 候选。 */
public final class AiMybatisSession {
    private final DataSource source;
    private final SqlSessionFactory factory;
    private final SqlSessionTemplate template;

    public AiMybatisSession(DataSource source) {
        if (source == null) { throw new IllegalArgumentException("AI MyBatis 数据源缺失"); }
        this.source = source;
        try {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.setCacheEnabled(false);
            configuration.setJdbcTypeForNull(JdbcType.NULL);
            configuration.setLogImpl(NoLoggingImpl.class);
            MybatisSqlSessionFactoryBean builder = new MybatisSqlSessionFactoryBean();
            builder.setDataSource(source);
            builder.setConfiguration(configuration);
            GlobalConfig global = new GlobalConfig();
            global.setBanner(false);
            global.setDbConfig(new GlobalConfig.DbConfig());
            builder.setGlobalConfig(global);
            factory = builder.getObject();
            configuration.addMapper(AiConversationMapper.class);
            configuration.addMapper(AiMessageMapper.class);
            configuration.addMapper(AiMemberMapper.class);
            configuration.addMapper(AiOriginMapper.class);
            configuration.addMapper(AiExecutionMapper.class);
            template = new SqlSessionTemplate(factory);
        } catch (Exception failure) {
            throw new IllegalStateException("AI MyBatis 会话工厂初始化失败", failure);
        }
    }

    public boolean usesSource(DataSource expected) { return source == expected; }
    public <T> T mapper(Class<T> type) { return template.getMapper(type); }

    /** 仅将已核实同源的私有会话工厂加入事务资源白名单，不能放过宿主其他数据源。 */
    public AiTransactionExecutor executor(PlatformTransactionManager manager, AiTransactionMode mode) {
        if (!(manager instanceof DataSourceTransactionManager)
                || ((DataSourceTransactionManager) manager).getDataSource() != source) {
            throw new IllegalStateException("AI MyBatis 事务管理器与会话工厂不同源");
        }
        return new AiTransactionExecutor(manager, mode, Collections.singleton(factory));
    }
}
