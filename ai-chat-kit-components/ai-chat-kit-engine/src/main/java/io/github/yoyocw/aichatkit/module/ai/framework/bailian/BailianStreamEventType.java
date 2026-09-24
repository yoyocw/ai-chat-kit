package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

/**
 * 百炼流式事件类型，用于区分可展示的处理进度与最终回答增量。
 */
public enum BailianStreamEventType {

    /** 已脱敏的智能体处理进度。 */
    PROGRESS,
    /** 最终回答的增量文本。 */
    DELTA
}
