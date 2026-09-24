package io.github.yoyocw.aichatkit.module.ai.enums;

/**
 * AI 群聊业务常量，定义编排规模、输出边界和总控展示信息。
 */
public final class AiGroupChatConstants {

    /** 工作流总控在无具体成员回复时使用的编码。 */
    public static final String AGENT_ORCHESTRATOR = "ORCHESTRATOR";
    /** 工作流最终汇总消息的展示名称。 */
    public static final String AGENT_ORCHESTRATOR_NAME = "协同总控";

    /** 群聊允许的最少成员数。 */
    public static final int MIN_MEMBER_COUNT = 2;
    /** 群聊允许的最多成员数。 */
    public static final int MAX_MEMBER_COUNT = 3;
    /** 传给工作流的本地历史摘要最大字符数。 */
    public static final int MAX_HISTORY_SUMMARY_LENGTH = 4000;
    /** 自动群聊标题最大 Unicode 字符数。 */
    public static final int AUTO_TITLE_MAX_LENGTH = 30;

    private AiGroupChatConstants() {
    }
}
