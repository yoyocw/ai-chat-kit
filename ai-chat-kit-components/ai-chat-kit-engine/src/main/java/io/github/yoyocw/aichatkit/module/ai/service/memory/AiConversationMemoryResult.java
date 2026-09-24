package io.github.yoyocw.aichatkit.module.ai.service.memory;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AI 会话记忆计算结果，包含本轮模型上下文和需要持久化的滚动记忆状态。
 */
@Data
@AllArgsConstructor
public class AiConversationMemoryResult {

    /** 提供给模型的历史上下文，由滚动摘要和最近原始消息组成。 */
    private String context;
    /** 已折叠旧消息形成的抽取式滚动摘要。 */
    private String summary;
    /** 已进入滚动摘要的最大消息编号；尚未折叠旧消息时为空。 */
    private Long cursorMessageId;
    /** 本轮是否推进了摘要或游标，用于避免无意义数据库更新。 */
    private boolean changed;
}
