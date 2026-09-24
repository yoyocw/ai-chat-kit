package io.github.yoyocw.aichatkit.module.ai.service.responsedata;

import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianGroupOutputException;
import io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo.AiGroupChatResultDataBO;
import io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo.AiResponseDataEnvelopeBO;
import io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo.AiResponseDataSourceBO;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessPresentation;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.SCHEMA_VERSION_V1;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.TYPE_GROUP_CHAT_RESULT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.TYPE_MAP_MISSION_LIST;

/**
 * AI 消息结构化扩展结果服务实现，使用字段白名单代替对百炼原始输出的黑名单清理。
 */
@Slf4j
@Service
public class AiResponseDataServiceImpl implements AiResponseDataService {

    @Override
    public String renderBusinessPresentation(Long messageId, AiBusinessPresentation presentation) {
        if (presentation == null) {
            return null;
        }
        try {
            // 第一阶段仅接受既有受控适配器格式，不开放任意展示类型。
            JsonNode data = JsonUtils.parseTree(presentation.getDataJson());
            if (!TYPE_MAP_MISSION_LIST.equals(presentation.getType())
                    || !SCHEMA_VERSION_V1.equals(presentation.getSchemaVersion())
                    || data == null || !data.isObject() || !data.path("items").isArray()) {
                throw new IllegalArgumentException("无效业务展示格式");
            }
            // 保持回答完成时生成 generatedAt 的既有时机。
            return serializeEnvelope(presentation.getType(), data);
        } catch (Exception ex) {
            // 地图扩展属于可选展示能力，失败日志只保留消息编号和异常类型，禁止记录业务载荷。
            log.warn("[renderBusinessPresentation][扩展结果序列化失败 messageId={} exceptionType={}]",
                    messageId, ex.getClass().getName());
            return null;
        }
    }

    @Override
    public String buildGroupChatResult(JsonNode responseData) {
        if (responseData == null || responseData.isNull()) {
            return null;
        }
        if (!responseData.isObject()) {
            throw invalidGroupResponseData();
        }
        AiGroupChatResultDataBO data = new AiGroupChatResultDataBO();
        data.setStatus(readOptionalText(responseData, "status"));
        data.setTraceCode(readOptionalText(responseData, "traceCode"));
        data.setFinalAnswer(readOptionalText(responseData, "finalAnswer"));
        data.setCompleted(readOptionalBoolean(responseData, "completed"));
        data.setStopReason(readOptionalText(responseData, "stopReason"));
        try {
            return serializeEnvelope(TYPE_GROUP_CHAT_RESULT, data);
        } catch (Exception ex) {
            throw invalidGroupResponseData();
        }
    }

    /** 读取可选文本白名单字段，并拒绝 Jackson 的隐式类型转换。 */
    private String readOptionalText(JsonNode responseData, String fieldName) {
        JsonNode value = responseData.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidGroupResponseData();
        }
        return value.textValue();
    }

    /** 读取可选布尔白名单字段，并拒绝字符串或数字到布尔值的隐式转换。 */
    private Boolean readOptionalBoolean(JsonNode responseData, String fieldName) {
        JsonNode value = responseData.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            throw invalidGroupResponseData();
        }
        return value.booleanValue();
    }

    /** 组装通用信封并生成包含系统时区偏移量的 ISO-8601 时间。 */
    private <T> String serializeEnvelope(String type, T data) {
        AiResponseDataEnvelopeBO<T> envelope = new AiResponseDataEnvelopeBO<T>();
        envelope.setSchemaVersion(SCHEMA_VERSION_V1);
        envelope.setType(type);
        envelope.setData(data);
        envelope.setSources(Collections.<AiResponseDataSourceBO>emptyList());
        envelope.setGeneratedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return JsonUtils.toJsonString(envelope);
    }

    /** 返回不包含百炼原始节点内容的统一群聊输出异常。 */
    private BailianGroupOutputException invalidGroupResponseData() {
        return new BailianGroupOutputException("工作流 responseData 不符合安全扩展结果契约");
    }
    @Override
    public String mergeSources(String envelope, String output) {
        JsonNode raw = output == null ? null : JsonUtils.parseTree(output);
        com.fasterxml.jackson.databind.node.ArrayNode references =
                io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianCitationSupport.merge(
                        null, raw == null ? null : raw.path("doc_references"));
        if (references.isEmpty()) { return envelope; }
        com.fasterxml.jackson.databind.node.ObjectNode result = (com.fasterxml.jackson.databind.node.ObjectNode)
                JsonUtils.parseTree(envelope == null ? serializeEnvelope(
                        io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.TYPE_ANSWER_SOURCES,
                        Collections.emptyMap()) : envelope);
        com.fasterxml.jackson.databind.node.ArrayNode sources = result.putArray("sources");
        for (JsonNode reference : references) {
            com.fasterxml.jackson.databind.node.ObjectNode source = sources.addObject();
            source.put("indexId", reference.path("index_id").asText());
            source.put("sourceType", "DOCUMENT_REFERENCE");
            source.put("title", reference.path("title").asText());
            if (reference.has("doc_url")) { source.put("url", reference.get("doc_url").asText()); }
            else { source.putNull("url"); }
        }
        return result.toString();
    }}
