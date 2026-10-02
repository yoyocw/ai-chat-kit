package io.github.yoyocw.aichatkit.ai.adapter.jdbc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 专用资源配置；凭据由部署秘密提供，不从宿主 spring.datasource 隐式复制。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-chat-kit.ai.storage.jdbc")
public class AiJdbcResourceProperties {
    /** 默认兼容原单源接入；独立资源必须明确选择 isolated。 */
    private AiJdbcResourceMode mode = AiJdbcResourceMode.REUSE;
    /** reference 模式指定的宿主 DataSource Bean 名称。 */
    private String dataSourceBean;
    /** reference 模式指定的宿主 JDBC 事务管理器 Bean 名称，必须绑定同一个 DataSource。 */
    private String transactionManagerBean;
    /** isolated 模式 PostgreSQL JDBC URL，不输出到日志或错误消息。 */
    private String jdbcUrl;
    /** isolated 模式数据库账号，不继承宿主默认账号。 */
    private String username;
    /** isolated 模式数据库密码，仅传给自有连接池，禁止输出完整配置对象。 */
    private String password;
    /** 自有池最大连接数，1..1000，默认 5。 */
    private int maximumPoolSize = 5;
    /** 自有池获取连接超时毫秒，250..300000，默认 5000；不等于业务端到端期限。 */
    private long connectionTimeoutMs = 5000;
    /** 自有池连接有效性检查超时毫秒，250..connectionTimeoutMs，默认 1000。 */
    private long validationTimeoutMs = 1000;
}
