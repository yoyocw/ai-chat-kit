package io.github.yoyocw.aichatkit.compat.framework.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * MCP 短期服务 JWT 配置，私钥仅部署在签发方，公钥部署在提供 MCP 接口的业务服务。
 */
@Data
@ConfigurationProperties(prefix = "aichatkit.security.mcp-jwt")
public class McpServiceJwtProperties {

    /** JWT 签发方标识，签发与验签服务必须一致。 */
    private String issuer = "platform-ai";
    /** JWT 接收方标识，防止令牌被其他系统复用。 */
    private String audience = "platform-business";
    /** PKCS#8 RSA 私钥 PEM，保留编解码兼容；当前签发从认证侧数据库读取，AI 不配置。 */
    private String privateKey;
    /** X.509 RSA 公钥 PEM，仅业务验签服务配置，可通过环境变量注入。 */
    private String publicKey;
    /** 业务服务是否接受动态 JWT；紧急撤销时设为 false 并发布所有验签实例，不影响固定密钥。 */
    private boolean verificationEnabled = true;
    /** 签发时间允许的时钟偏差秒数，范围 0 至 30；不延长登录令牌的到期时间。 */
    private int clockSkewSeconds = 10;
    /** 机器身份分页上限，允许范围 1 至 200 条。 */
    private int maxPageSize = 100;
    /** 认证侧签发及业务服务验签允许的只读 MCP 端点编码白名单。 */
    private Set<String> allowedEndpointCodes = new LinkedHashSet<String>(Arrays.asList(
            "fac.aerial-mission.get", "fac.aerial-mission.page",
            "forest.monitor.task.get", "forest.monitor.task.page",
            "woodland.analysis.task.get", "woodland.analysis.task.page"));
}
