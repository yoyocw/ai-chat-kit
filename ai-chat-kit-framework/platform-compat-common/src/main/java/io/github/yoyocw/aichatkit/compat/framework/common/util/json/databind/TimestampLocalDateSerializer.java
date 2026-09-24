package io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于时间戳的 LocalDate 序列化器
 *
 * @author 老五
 */
@Slf4j
public class TimestampLocalDateSerializer extends JsonSerializer<LocalDate> {

    public static final TimestampLocalDateSerializer INSTANCE = new TimestampLocalDateSerializer();

    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    @Override
    public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // 情况一：有 JsonFormat 自定义注解，则使用它
        String fieldName = gen.getOutputContext().getCurrentName();
        if (fieldName != null) {
            Object currentValue = gen.getOutputContext().getCurrentValue();
            if (currentValue != null) {
                Class<?> clazz = currentValue.getClass();
                Map<String, Field> fieldMap = FIELD_CACHE.computeIfAbsent(clazz, this::buildFieldMap);
                Field field = fieldMap.get(fieldName);
                if (field != null && field.isAnnotationPresent(JsonFormat.class)) {
                    JsonFormat jsonFormat = field.getAnnotation(JsonFormat.class);
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(jsonFormat.pattern());
                        gen.writeString(formatter.format(value));
                        return;
                    } catch (Exception ex) {
                        log.warn("[serialize][({}#{}) 使用 JsonFormat pattern 失败，尝试使用默认的 Long 时间戳]",
                                clazz.getName(), fieldName, ex);
                    }
                }
            }
        }

        // 情况二：默认将 LocalDate 转换为当天的零点时间戳
        gen.writeNumber(value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private Map<String, Field> buildFieldMap(Class<?> clazz) {
        Map<String, Field> fieldMap = new HashMap<>();
        for (Field field : ReflectUtil.getFields(clazz)) {
            String fieldName = field.getName();
            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
            if (jsonProperty != null) {
                String value = jsonProperty.value();
                if (StrUtil.isNotEmpty(value) && ObjUtil.notEqual("\u0000", value)) {
                    fieldName = value;
                }
            }
            fieldMap.put(fieldName, field);
        }
        return fieldMap;
    }

}
