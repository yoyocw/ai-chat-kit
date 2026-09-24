package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 本地委托签发切换配置；由运维提供原签发秘密，不初始化或写入数据库。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-chat-kit.ai.local-delegation-signing")
public class AiLocalDelegationSigningProperties {
    /** 默认关闭；启用前必须停用旧签发路由并核对原 issuer、audience 和公钥。 */
    private boolean enabled;
    /** 明确密钥来源；默认秘密配置，DATABASE 只读 AI 自有存储，任何失败均不切换来源。 */
    private AiSigningKeySource source = AiSigningKeySource.CONFIGURATION;
    /** 通过宿主秘密配置注入的原 PKCS#8 私钥；禁止写入样例、日志、返回值或 toString。 */
    private String privateKey;
}
