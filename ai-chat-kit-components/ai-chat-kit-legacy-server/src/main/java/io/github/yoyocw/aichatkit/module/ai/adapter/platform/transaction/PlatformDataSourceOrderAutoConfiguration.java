package io.github.yoyocw.aichatkit.module.ai.adapter.platform.transaction;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;

/**
 * 林业 server 的动态数据源装配顺序约束，不创建数据源或事务管理器。
 * 动态数据源定义必须先于 Boot 的单数据源候选判断，否则后续实例化数据源不会补评事务条件。
 * 保留 Boot 对宿主已有事务管理器的退让及林业执行器的实际同源校验。
 */
@AutoConfiguration(afterName = "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration",
        before = DataSourceTransactionManagerAutoConfiguration.class)
public class PlatformDataSourceOrderAutoConfiguration {
}
