package io.github.yoyocw.aichatkit.module.ai.framework.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.LocalDateTime;

/** 引擎专用 JSON 协议编码，不读取宿主全局工具配置，也不记录输入原文。 */
public final class AiEngineJson {
    /** 初始化后不再修改或向调用方暴露的线程安全协议映射器。 */
    private static final ObjectMapper MAPPER = createMapper();

    private AiEngineJson() { }

    /** 保留非空字段、未知字段容忍及现有毫秒时间戳协议。 */
    private static ObjectMapper createMapper() {
        JavaTimeModule timeModule = new JavaTimeModule();
        timeModule.addSerializer(LocalDateTime.class, new AiLocalDateTimeSerializer());
        timeModule.addDeserializer(LocalDateTime.class, new AiLocalDateTimeDeserializer());
        return new ObjectMapper().registerModule(timeModule)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /** @param value 协议对象或动态事件字段 @return JSON 文本；失败不暴露原始对象 */
    public static String toJsonString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException("AI JSON serialization failed");
        }
    }

    /** @param text JSON 文本 @return JSON 树；格式错误转换为不含输入原文的异常 */
    public static JsonNode parseTree(String text) {
        try {
            return readTree(text);
        } catch (IOException ex) {
            throw new IllegalArgumentException("AI JSON parsing failed");
        }
    }

    /** @param text 上游文本 @return JSON 树 @throws IOException 格式错误，由调用方分类处理 */
    public static JsonNode readTree(String text) throws IOException {
        return MAPPER.readTree(text);
    }

    /** @param text 可为空的模型输出 @param type 目标类型 @return 空输入返回 null */
    public static <T> T parseObject(String text, Class<T> type) {
        if (text == null || text.isEmpty()) { return null; }
        try {
            return MAPPER.readValue(text, type);
        } catch (IOException ex) {
            throw new IllegalArgumentException("AI JSON parsing failed");
        }
    }

    /** @return 独立可写 JSON 数组，不暴露共享映射器 */
    public static ArrayNode createArrayNode() { return MAPPER.createArrayNode(); }

    /** @return 独立可写 JSON 对象，不暴露共享映射器 */
    public static ObjectNode createObjectNode() { return MAPPER.createObjectNode(); }
}
