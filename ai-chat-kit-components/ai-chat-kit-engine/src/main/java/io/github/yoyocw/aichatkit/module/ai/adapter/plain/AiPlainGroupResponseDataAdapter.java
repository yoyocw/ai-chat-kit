package io.github.yoyocw.aichatkit.module.ai.adapter.plain;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelException;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.GROUP_WORKFLOW_OUTPUT_INVALID;
import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.TYPE_GROUP_CHAT_RESULT;

/** 群聊通用汇总展示，沿用原受控字段与严格类型检查，引用复用引擎安全过滤。 */
public final class AiPlainGroupResponseDataAdapter implements AiGroupResponseDataPort {
    /** 可选展示为空不伪造结果；已提供的展示类型错误时必须终止本轮完成。 */
    @Override
    public String build(String responseJson) {
        if (responseJson == null) { return null; }
        JsonNode raw;
        try { raw = AiEngineJson.parseTree(responseJson); }
        catch (RuntimeException ex) { throw invalid(); }
        if (raw == null || raw.isNull()) { return null; }
        if (!raw.isObject()) { throw invalid(); }
        ObjectNode data = AiEngineJson.createObjectNode();
        for (String field : new String[]{"status", "traceCode", "finalAnswer", "stopReason"}) {
            JsonNode value = raw.get(field);
            if (value == null || value.isNull()) { continue; }
            if (!value.isTextual()) { throw invalid(); }
            data.put(field, value.textValue());
        }
        JsonNode completed = raw.get("completed");
        if (completed != null && !completed.isNull()) {
            if (!completed.isBoolean()) { throw invalid(); }
            data.put("completed", completed.booleanValue());
        }
        return AiPlainResponseEnvelope.create(TYPE_GROUP_CHAT_RESULT, data, raw.path("doc_references")).toString();
    }

    /** 异常仅含固定安全说明，不携带完整模型响应。 */
    private IllegalArgumentException invalid() {
        return new IllegalArgumentException(GROUP_WORKFLOW_OUTPUT_INVALID.getMsg(),
                new AiModelException(GROUP_WORKFLOW_OUTPUT_INVALID, false));
    }
}
