package io.github.yoyocw.aichatkit.module.ai.contract.model;

/** Immutable display-safe event. */
public final class AiModelEvent {
    private final AiModelEventType type;
    private final String stage;
    private final String message;
    private final String actionName;
    private final String content;
    private AiModelEvent(AiModelEventType type, String stage, String message, String actionName, String content) {
        this.type = type;
        this.stage = stage;
        this.message = message;
        this.actionName = actionName;
        this.content = content;
    }

    public static AiModelEvent delta(String content) {
        return new AiModelEvent(AiModelEventType.DELTA, null, null, null, content);
    }

    public static AiModelEvent progress(String stage, String message, String actionName) {
        return new AiModelEvent(AiModelEventType.PROGRESS, stage, message, actionName, null);
    }

    public AiModelEventType getType() {
        return type;
    }

    public String getStage() {
        return stage;
    }

    public String getMessage() {
        return message;
    }

    public String getActionName() {
        return actionName;
    }

    public String getContent() {
        return content;
    }
}
