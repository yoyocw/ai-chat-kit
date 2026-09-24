package io.github.yoyocw.aichatkit.ai.starter.config;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.EnumSet;
import java.util.Set;

/** 声明需要装配的业务模式；模型总开关沿用 ai-chat-kit.ai.engine.enabled，不重复定义。 */
@ConfigurationProperties(prefix = "ai-chat-kit.ai.starter")
public class AiStarterProperties {
    /** 当前宿主部署固定身份命名空间；启用时必填，不等同于 JWT issuer，禁止请求覆盖。 */
    private String namespace;

    /** @return 部署固定命名空间 */
    public String getNamespace() { return namespace; }

    /** @param namespace YAML 中声明的宿主身份命名空间 */
    public void setNamespace(String namespace) { this.namespace = namespace; }

    /** 必需模式集合，默认仅单聊；启用 Starter 时不能为空，不能据此绕过所选模式的适配要求。 */
    private Set<AiChatMode> modes = EnumSet.of(AiChatMode.SINGLE);

    /** @return 宿主声明的模式集合，装配检查时建立快照 */
    public Set<AiChatMode> getModes() { return modes; }

    /** @param modes YAML 声明的 SINGLE/GROUP 集合，非法值由绑定失败拒绝 */
    public void setModes(Set<AiChatMode> modes) { this.modes = modes; }
}
