package io.github.yoyocw.aichatkit.module.ai.framework.json;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 保留既有本地日期时间毫秒时间戳，以及字段显式 JsonFormat 格式。 */
final class AiLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> implements ContextualSerializer {
    /** 字段自定义格式，为空时按系统时区输出 Unix 毫秒。 */
    private final DateTimeFormatter formatter;

    AiLocalDateTimeSerializer() { this(null); }
    private AiLocalDateTimeSerializer(DateTimeFormatter formatter) { this.formatter = formatter; }

    /** 按字段格式输出字符串，否则保持现有毫秒协议。 */
    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider)
            throws IOException {
        if (formatter != null) { generator.writeString(formatter.format(value)); }
        else { generator.writeNumber(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()); }
    }

    /** 仅为显式声明格式的属性创建实例，不修改共享序列化器。 */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
            throws JsonMappingException {
        JsonFormat annotation = property == null ? null : property.getAnnotation(JsonFormat.class);
        if (annotation != null && !annotation.pattern().isEmpty()) {
            try { return new AiLocalDateTimeSerializer(DateTimeFormatter.ofPattern(annotation.pattern())); }
            catch (IllegalArgumentException ignored) { /* 与既有无效格式的毫秒回退保持一致。 */ }
        }
        return this;
    }
}
