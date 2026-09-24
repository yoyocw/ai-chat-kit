package io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 基于时间戳的 LocalDate 反序列化器
 * <p>
 * 同时支持两种格式：
 * <ul>
 *     <li>数值（Long 时间戳 / epoch millis）</li>
 *     <li>字符串（yyyy-MM-dd）</li>
 * </ul>
 *
 * @author 老五
 */
public class TimestampLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    public static final TimestampLocalDateDeserializer INSTANCE = new TimestampLocalDateDeserializer();

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        // 字符串格式：yyyy-MM-dd
        if (token == JsonToken.VALUE_STRING) {
            return LocalDate.parse(p.getText(), FORMATTER);
        }
        // 数值格式：Long 时间戳，转换为 LocalDate 对象
        return Instant.ofEpochMilli(p.getValueAsLong()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

}
