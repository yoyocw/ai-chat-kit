package io.github.yoyocw.aichatkit.compat.framework.jackson.config;

import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind.BigDecimalSerializer;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind.NumberSerializer;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind.TimestampLocalDateDeserializer;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind.TimestampLocalDateSerializer;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind.TimestampLocalDateTimeDeserializer;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind.TimestampLocalDateTimeSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@Slf4j
public class AiChatKitJacksonAutoConfiguration {

    /**
     * 从 Builder 源头定制（关键：使用 *ByType，避免 handledType 要求）
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer ldtEpochMillisCustomizer() {
        return builder -> builder
                // Long -> Number
                .serializerByType(Long.class, NumberSerializer.INSTANCE)
                .serializerByType(Long.TYPE, NumberSerializer.INSTANCE)
                // BigDecimal -> 去除末尾多余的 0（13.00 -> 13，13.20 -> 13.2）
                .serializerByType(BigDecimal.class, BigDecimalSerializer.INSTANCE)
                // LocalDate < - > EpochMillis（当天零点时间戳）
                .serializerByType(LocalDate.class, TimestampLocalDateSerializer.INSTANCE)
                .deserializerByType(LocalDate.class, TimestampLocalDateDeserializer.INSTANCE)
                // LocalTime
                .serializerByType(LocalTime.class, LocalTimeSerializer.INSTANCE)
                .deserializerByType(LocalTime.class, LocalTimeDeserializer.INSTANCE)
                // LocalDateTime < - > EpochMillis
                .serializerByType(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE)
                .deserializerByType(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);
    }

    /**
     * 以 Bean 形式暴露 Module（Boot 会自动注册到所有 ObjectMapper）
     */
    @Bean
    public Module timestampSupportModuleBean() {
        SimpleModule m = new SimpleModule("TimestampSupportModule");
        // Long -> Number，避免前端精度丢失
        m.addSerializer(Long.class, NumberSerializer.INSTANCE);
        m.addSerializer(Long.TYPE, NumberSerializer.INSTANCE);
        // BigDecimal -> 去除末尾多余的 0（13.00 -> 13，13.20 -> 13.2）
        m.addSerializer(BigDecimal.class, BigDecimalSerializer.INSTANCE);
        // LocalDate < - > EpochMillis（当天零点时间戳）
        m.addSerializer(LocalDate.class, TimestampLocalDateSerializer.INSTANCE);
        m.addDeserializer(LocalDate.class, TimestampLocalDateDeserializer.INSTANCE);
        // LocalTime
        m.addSerializer(LocalTime.class, LocalTimeSerializer.INSTANCE);
        m.addDeserializer(LocalTime.class, LocalTimeDeserializer.INSTANCE);
        // LocalDateTime < - > EpochMillis
        m.addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE);
        m.addDeserializer(LocalDateTime.class, TimestampLocalDateTimeDeserializer.INSTANCE);
        return m;
    }

    /**
     * 初始化全局 JsonUtils，直接使用主 ObjectMapper
     */
    @Bean
    @SuppressWarnings("InstantiationOfUtilityClass")
    public JsonUtils jsonUtils(ObjectMapper objectMapper) {
        JsonUtils.init(objectMapper);
        log.debug("[init][初始化 JsonUtils 成功]");
        return new JsonUtils();
    }

}
