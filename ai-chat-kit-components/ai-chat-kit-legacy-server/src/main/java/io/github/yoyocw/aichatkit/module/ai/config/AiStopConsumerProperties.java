package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 停止核验专用机器身份，只从部署配置读取，不接收对话或用户参数。 */
@Component
@ConfigurationProperties(prefix = "ai-chat-kit.ai.stop-consumer")
@Getter
@Setter
@EqualsAndHashCode
public class AiStopConsumerProperties {
    /** 默认关闭；缺配置不影响旧发送、停止及 MCP 链路启动。 */
    private volatile boolean enabled;
    /** system 固定 HTTPS origin，只允许空路径或根路径，无查询和用户信息。 */
    private volatile String baseUrl;
    /** 独立消费者客户端标识，不能复用原业务客户端。 */
    private volatile String clientId;
    /** 部署注入的客户端秘密，禁止生成 toString 或记录配置对象。 */
    private volatile String clientSecret;
    /** 消费者所属部署租户，非当前请求用户租户，必须非负。 */
    private volatile Long tenantId;

    /** @return 当前配置副本，用于绑定缓存并在远程申请后检查配置是否变化。 */
    public AiStopConsumerProperties snapshot() {
        AiStopConsumerProperties copy = new AiStopConsumerProperties();
        copy.setEnabled(enabled);
        copy.setBaseUrl(baseUrl);
        copy.setClientId(clientId);
        copy.setClientSecret(clientSecret);
        copy.setTenantId(tenantId);
        return copy;
    }
}
