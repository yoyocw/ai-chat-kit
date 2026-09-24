package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 同一次授权查询生成的事实与展示快照，不含宿主 DTO。 */
public final class AiBusinessSnapshot {
    /** 是否实际请求并完成业务查询。 */
    private final AiBusinessContextStatus status;
    /** 提供给模型的裁剪业务事实 JSON，不含完整地图坐标或凭据。 */
    private final String promptFactsJson;
    /** 可选展示数据，转换失败时可为空，不改变已成功的业务事实。 */
    private final AiBusinessPresentation presentation;

    public AiBusinessSnapshot(AiBusinessContextStatus status, String promptFactsJson,
                               AiBusinessPresentation presentation) {
        this.status = status;
        this.promptFactsJson = promptFactsJson;
        this.presentation = presentation;
    }
    public AiBusinessContextStatus getStatus() { return status; }
    public String getPromptFactsJson() { return promptFactsJson; }
    public AiBusinessPresentation getPresentation() { return presentation; }
}
