package io.github.yoyocw.aichatkit.module.ai.contract.config;

/** 固定对话模式，由服务入口选择，不能传递任意配置键。 */
public enum AiChatMode {
    /** 单聊。 */
    SINGLE,
    /** 群聊工作流。 */
    GROUP
}
