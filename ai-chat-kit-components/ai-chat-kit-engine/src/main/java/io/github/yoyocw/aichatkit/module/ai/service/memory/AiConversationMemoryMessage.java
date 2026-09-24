package io.github.yoyocw.aichatkit.module.ai.service.memory;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AI 会话记忆消息，统一承载单聊和群聊进入滚动记忆算法所需的最小字段。
 */
@Data
@AllArgsConstructor
public class AiConversationMemoryMessage {

    /** 消息主键编号，用于确定历史顺序和推进摘要游标。 */
    private Long id;
    /** 消息发言者展示名称，例如用户、助手或群聊智能体名称。 */
    private String speaker;
    /** 已完成消息原文；滚动摘要只做抽取式归档，不改写事实。 */
    private String content;
}
