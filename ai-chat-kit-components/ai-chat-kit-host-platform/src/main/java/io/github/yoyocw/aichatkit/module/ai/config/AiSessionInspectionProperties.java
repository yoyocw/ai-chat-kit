package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/** AI 受限会话复核配置；显式开启后替换委托入口认证，不自动申请或续期消费者令牌。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-chat-kit.ai.session-inspection")
public class AiSessionInspectionProperties {
    /** 默认关闭；认证侧接口和消费者策略部署完成后才可显式开启。 */
    private boolean enabled;
    /** 固定认证服务 HTTPS 源站，仅协议、主机和可选端口，不允许请求指定地址。 */
    private String baseUrl;
    /** 独立受限消费者的原始访问令牌，不含 Bearer；由秘密配置注入，禁止日志和 toString。 */
    private String consumerAccessToken;
    /** 消费者机器所属租户，来自可信部署配置，不读取请求 tenant-id。 */
    private Long consumerTenantId;
    /** 当前 AI 部署允许查询的主体租户；首次凭据查询也须使用此值，不接受任意租户头。 */
    private Long subjectTenantId;
    /** AI 自有业务接入绑定；默认空列表拒绝所有调用方，不从外部请求或认证响应补全。 */
    private List<AiInspectionCallerBinding> callers = new ArrayList<>();
}
