package io.github.yoyocw.aichatkit.module.ai.enums;

/**
 * AI 消息结构化扩展结果协议常量，供服务端写入与前端消费时识别稳定版本和业务类型。
 */
public final class AiResponseDataConstants {

    /** 当前结构化扩展结果协议版本。 */
    public static final String SCHEMA_VERSION_V1 = "AI_RESPONSE_DATA_V1";
    /** 地图飞防任务列表结果类型。 */
    public static final String TYPE_MAP_MISSION_LIST = "MAP_MISSION_LIST";
    /** 多智能体群聊汇总结果类型。 */
    public static final String TYPE_GROUP_CHAT_RESULT = "GROUP_CHAT_RESULT";

    /** 普通回答仅包含引用来源时的结果类型。 */
    public static final String TYPE_ANSWER_SOURCES = "ANSWER_SOURCES";

    private AiResponseDataConstants() {
    }
}
