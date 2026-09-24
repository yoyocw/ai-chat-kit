package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 宿主生成的展示数据，只有 data 部分；时间和来源在完成响应时组装。 */
public final class AiBusinessPresentation {
    /** 已注册的业务展示类型，不是模型自由指定的类型。 */
    private final String type;
    /** 展示信封版本。 */
    private final String schemaVersion;
    /** 已按宿主字段契约转换的 JSON 对象，无凭据、来源及生成时间。 */
    private final String dataJson;

    public AiBusinessPresentation(String type, String schemaVersion, String dataJson) {
        this.type = type;
        this.schemaVersion = schemaVersion;
        this.dataJson = dataJson;
    }
    public String getType() { return type; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getDataJson() { return dataJson; }
}
