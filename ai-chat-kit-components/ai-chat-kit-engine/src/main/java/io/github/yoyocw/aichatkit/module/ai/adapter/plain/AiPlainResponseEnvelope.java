package io.github.yoyocw.aichatkit.module.ai.adapter.plain;

import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianCitationSupport;
import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.SCHEMA_VERSION_V1;

/** 纯聊天展示信封与引用白名单共用实现，不复制业务实体或模型原始节点。 */
final class AiPlainResponseEnvelope {
    private AiPlainResponseEnvelope() { }

    /** 由已筛选展示字段创建固定版本信封，生成时间不使用模型自报值。 */
    static ObjectNode create(String type, ObjectNode data, JsonNode references) {
        ObjectNode result = AiEngineJson.createObjectNode();
        result.put("schemaVersion", SCHEMA_VERSION_V1); result.put("type", type); result.set("data", data);
        ArrayNode sources = result.putArray("sources");
        for (JsonNode reference : BailianCitationSupport.merge(null, references)) {
            ObjectNode source = sources.addObject();
            source.put("indexId", reference.path("index_id").asText()); source.put("sourceType", "DOCUMENT_REFERENCE");
            source.put("title", reference.path("title").asText());
            if (reference.has("doc_url")) { source.put("url", reference.get("doc_url").asText()); }
            else { source.putNull("url"); }
        }
        result.put("generatedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return result;
    }

    /** 将既有纯引用信封转换回引用白名单输入；链接仍需通过同一安全过滤器。 */
    static ArrayNode references(JsonNode envelope) {
        ArrayNode references = AiEngineJson.createArrayNode();
        JsonNode sources = envelope.path("sources");
        if (!sources.isArray()) { throw new IllegalArgumentException("纯聊天引用信封格式无效"); }
        for (JsonNode source : sources) {
            if (!"DOCUMENT_REFERENCE".equals(source.path("sourceType").asText())) { continue; }
            ObjectNode reference = references.addObject();
            reference.set("index_id", source.path("indexId")); reference.set("title", source.path("title"));
            reference.set("doc_url", source.path("url"));
        }
        return BailianCitationSupport.merge(null, references);
    }
}
