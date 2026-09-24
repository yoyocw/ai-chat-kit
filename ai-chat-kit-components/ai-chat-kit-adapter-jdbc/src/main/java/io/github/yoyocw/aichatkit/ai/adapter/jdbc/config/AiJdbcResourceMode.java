package io.github.yoyocw.aichatkit.ai.adapter.jdbc.config;

/** AI JDBC 资源的部署选择方式，不由请求改变。 */
public enum AiJdbcResourceMode {
    /** 保留原宿主默认单源选择，已有 Primary 规则由宿主决定。 */
    REUSE,
    /** AI 私有真实 PostgreSQL 连接池和事务管理器，不注册全局资源候选。 */
    ISOLATED,
    /** 引用宿主明确命名且已经同源的资源，不管理其关闭。 */
    REFERENCE
}
