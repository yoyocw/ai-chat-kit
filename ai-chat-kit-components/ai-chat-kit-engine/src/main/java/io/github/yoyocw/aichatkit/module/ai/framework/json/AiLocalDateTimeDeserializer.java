package io.github.yoyocw.aichatkit.module.ai.framework.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 将既有 Unix 毫秒协议恢复为系统时区的本地日期时间。 */
final class AiLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
    /** @return 使用既有系统时区语义解释的本地日期时间 */
    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(parser.getValueAsLong()), ZoneId.systemDefault());
    }
}
