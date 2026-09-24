package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 单聊引擎输入视图，不依赖宿主Web验证注解或Controller类型；宿主仍须认证并校验输入。 */
public interface AiSingleChatRequest {
    /** @return 可选已有会话编号，首次发送允许为空 */
    Long getConversationId();
    /** @return 本轮问题，要求非空白且不超过10000字符 */
    String getContent();
    /** @return 是否请求宿主提供地图展示能力，null按关闭处理 */
    Boolean getMapEnabled();
}
