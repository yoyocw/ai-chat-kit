package io.github.yoyocw.aichatkit.module.ai.enums;

/**
 * AI 模型执行审计常量，统一单聊与群聊的执行模式和内部终态原因。
 */
public final class AiModelExecutionConstants {

    /** 普通智能对话执行模式。 */
    public static final String MODE_SINGLE = "single";
    /** 多智能体群聊工作流执行模式。 */
    public static final String MODE_GROUP = "group";

    /** 用户主动停止执行。 */
    public static final String ERROR_CODE_USER_STOPPED = "USER_STOPPED";
    /** 超过百炼读取超时后仍未收口的历史执行。 */
    public static final String ERROR_CODE_STALE_TIMEOUT = "STALE_TIMEOUT";

    private AiModelExecutionConstants() {
    }
}
