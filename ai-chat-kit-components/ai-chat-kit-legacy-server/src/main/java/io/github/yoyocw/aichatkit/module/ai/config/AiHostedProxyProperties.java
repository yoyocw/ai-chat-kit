package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 承接业务代理的部署边界；默认关闭，不从用户请求取得目标或客户端秘密。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-chat-kit.ai.business-proxy")
public class AiHostedProxyProperties {
    /** 认证端点敏感日志、实体绑定及路由部署就绪后才可开启。 */
    private boolean enabled;
    /** 固定认证 HTTPS 源站，无路径、查询或用户信息。 */
    private String authorizationBaseUrl;
    /** 固定 AI 委托入口 HTTPS 源站，无路径、查询或用户信息。 */
    private String aiBaseUrl;
    /** 专用机器客户端业务编号，不能复用 inspect 消费者。 */
    private String clientId;
    /** 专用机器客户端原实体编号，用于拒绝同名重建。 */
    private Long clientRecordId;
    /** 通过宿主秘密配置注入，不写样例、日志或 toString。 */
    private String clientSecret;
    /** 临时令牌最大寿命秒，30 至 900；认证侧该专用客户端必须配置不超过此值。 */
    private int maxTokenLifetimeSeconds = 300;
}
