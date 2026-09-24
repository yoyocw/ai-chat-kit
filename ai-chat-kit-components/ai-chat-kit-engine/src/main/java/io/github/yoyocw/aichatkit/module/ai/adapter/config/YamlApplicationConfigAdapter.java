package io.github.yoyocw.aichatkit.module.ai.adapter.config;

import io.github.yoyocw.aichatkit.module.ai.config.AiApplicationsProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiApplicationBindingProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import java.util.HashSet;

/** Spring配置绑定适配，部署级共用；身份与工具授权仍由独立授权端口每轮执行。 */
@RequiredArgsConstructor
public class YamlApplicationConfigAdapter implements AiApplicationConfigPort {
    /** Spring原生绑定的部署参数，不读任何配置表。 */
    private final AiApplicationsProperties properties;

    @Override
    public AiApplicationConfig load(AiInvocationContext context, AiChatMode mode) {
        // context保留统一接口，不以用户身份选择部署配置，也不在此授予任何权限。
        if (mode == null) {
            throw new IllegalStateException("缺少对话配置模式");
        }
        String key = "ai-chat-kit.ai.applications." + (mode == AiChatMode.SINGLE ? "single" : "group");
        AiApplicationBindingProperties config = mode == AiChatMode.SINGLE
                ? properties.getSingle() : properties.getGroup();
        validate(config, key);
        if (mode == AiChatMode.GROUP && StringUtils.hasText(config.getMcpId())) {
            throw new IllegalStateException("群聊原生 MCP Header 协议尚未验证，请配置用户级自定义插件工具 ID");
        }
        return new AiApplicationConfig(config.getAppId(), config.getMcpId(), config.getUserAuthToolIds());
    }

    /** 按使用模式延迟校验，不因未使用的模式缺配置阻止另一模式；错误不回显配置值。 */
    private void validate(AiApplicationBindingProperties config, String key) {
        if (config == null || config.getAppId() == null || !config.getAppId().matches("[a-fA-F0-9]{32}")) {
            throw new IllegalStateException("请在 YAML 或环境变量中设置 " + key + " 的有效 app-id");
        }
        if (config.getUserAuthToolIds() == null || config.getUserAuthToolIds().size() > 10
                || new HashSet<String>(config.getUserAuthToolIds()).size() != config.getUserAuthToolIds().size()
                || config.getUserAuthToolIds().stream().anyMatch(id -> id == null || id.length() > 128
                    || !id.matches("tool_[a-zA-Z0-9-]+"))) {
            throw new IllegalStateException(key + " 的工具 ID 列表无效");
        }
        if (config.getMcpId() != null && (config.getMcpId().length() > 128
                || !config.getMcpId().matches("[a-zA-Z0-9_-]*"))) {
            throw new IllegalStateException(key + " 的 MCP ID 无效");
        }
    }
}
