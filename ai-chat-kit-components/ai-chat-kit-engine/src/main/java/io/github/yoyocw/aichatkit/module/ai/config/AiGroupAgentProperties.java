package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Getter;
import lombok.Setter;

/** 部署者配置的群聊目录成员，不从客户端请求接受职责定义。 */
@Getter
@Setter
public class AiGroupAgentProperties {
    /** 规范大写工作流编码，允许A-Z/数字/下划线/短横线，最大128字符，禁止ORCHESTRATOR。 */
    private String code;
    /** 成员显示名称，最大256字符。 */
    private String name;
    /** 成员职责说明，最大4000字符。 */
    private String role;
    /** 是否启用，默认关闭，必须由部署明确授权。 */
    private boolean enabled;
}
