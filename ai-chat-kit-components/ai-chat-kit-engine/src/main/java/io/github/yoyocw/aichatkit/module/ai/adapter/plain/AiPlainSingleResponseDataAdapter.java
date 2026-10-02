package io.github.yoyocw.aichatkit.module.ai.adapter.plain;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessPresentation;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.framework.citation.AiCitationSupport;
import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.*;

/** 单聊默认展示只接收安全文档引用，业务展示必须由对应宿主专用适配处理。 */
public final class AiPlainSingleResponseDataAdapter implements AiSingleResponseDataPort {
    /** 未请求业务展示时返回空；不能把真实业务展示静默丢弃。 */
    @Override
    public String renderBusinessPresentation(Long messageId, AiBusinessPresentation presentation) {
        if (presentation != null) { throw new IllegalStateException("纯聊天展示适配不能处理业务展示，请配置对应展示适配"); }
        return null;
    }

    /** 仅保留编号、标题和安全HTTP(S)链接，不透传其他模型或信封字段。 */
    @Override
    public String mergeSources(String envelope, String output) {
        JsonNode previous = envelope == null ? null : AiEngineJson.parseTree(envelope);
        if (previous != null && (!previous.isObject() || !SCHEMA_VERSION_V1.equals(previous.path("schemaVersion").asText())
                || !TYPE_ANSWER_SOURCES.equals(previous.path("type").asText())
                || !previous.path("data").isObject() || previous.path("data").size() != 0)) {
            throw new IllegalArgumentException("纯聊天展示仅接受引用信封");
        }
        JsonNode raw = output == null ? null : AiEngineJson.parseTree(output);
        ArrayNode references = AiCitationSupport.merge(previous == null ? null : AiPlainResponseEnvelope.references(previous),
                raw == null ? null : raw.path("doc_references"));
        if (references.isEmpty()) { return null; }
        return AiPlainResponseEnvelope.create(TYPE_ANSWER_SOURCES, AiEngineJson.createObjectNode(), references).toString();
    }
}
