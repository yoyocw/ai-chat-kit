package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import lombok.Data;

/**
 * 百炼流式事件，仅承载允许前端展示的进度摘要或最终回答增量。
 */
@Data
public class BailianStreamEvent {

    /** 事件类型，用于区分处理进度和最终回答增量。 */
    private BailianStreamEventType type;
    /** 稳定处理阶段，例如 reasoning、tool_call、generating。 */
    private String stage;
    /** 已脱敏、可直接展示的处理进度文案。 */
    private String message;
    /** 已清理的工具或动作名称；无明确名称时为空。 */
    private String actionName;
    /** 最终回答增量；仅 DELTA 事件使用。 */
    private String content;

    /**
     * 创建处理进度事件。
     *
     * @param stage 稳定处理阶段
     * @param message 前端展示文案
     * @param actionName 已清理的动作名称
     * @return 处理进度事件
     */
    public static BailianStreamEvent progress(String stage, String message, String actionName) {
        BailianStreamEvent event = new BailianStreamEvent();
        event.setType(BailianStreamEventType.PROGRESS);
        event.setStage(stage);
        event.setMessage(message);
        event.setActionName(actionName);
        return event;
    }

    /**
     * 创建最终回答增量事件。
     *
     * @param content 本次新增的回答文本
     * @return 最终回答增量事件
     */
    public static BailianStreamEvent delta(String content) {
        BailianStreamEvent event = new BailianStreamEvent();
        event.setType(BailianStreamEventType.DELTA);
        event.setContent(content);
        return event;
    }
}
