package io.github.yoyocw.aichatkit.compat.framework.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** MCP 业务宿主受限会话核验配置；未启用时仅保留固定密钥认证，拒绝用户 JWT。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aichatkit.security.mcp-session-inspection")
public class McpSessionInspectionProperties {
    /** 显式开启新服务鉴权查询；失败不允许回退旧裸会话 ID RPC。 */
    private boolean enabled;
    /** 认证服务固定 HTTPS 源站，不带路径；开启后必须配置。 */
    private String baseUrl;
    /** 服务消费者自身租户，非负；不取自用户 JWT。 */
    private Long consumerTenantId;
    /** 默认供应器的原始服务访问令牌，不含 Bearer；通过秘密配置提供，生命周期由部署方管理，禁止输出日志。 */
    private String consumerAccessToken;
}
