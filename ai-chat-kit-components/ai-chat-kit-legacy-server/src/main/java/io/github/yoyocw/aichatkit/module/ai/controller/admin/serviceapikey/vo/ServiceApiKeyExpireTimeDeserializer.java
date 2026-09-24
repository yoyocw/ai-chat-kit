package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * MCP 调用方失效时间反序列化器。
 *
 * <p>仅用于管理端 MCP 密钥创建接口，将 {@code yyyy-MM-dd HH:mm:ss} 格式字符串转换为
 * {@link LocalDateTime}，避免改变项目其他接口沿用的时间戳反序列化规则。</p>
 */
public class ServiceApiKeyExpireTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    /** 对外时间格式说明。 */
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 使用 uuuu 执行严格公历年份解析，拒绝无效日期及自动进位。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * 将接口输入的失效时间字符串转换为本地日期时间。
     *
     * @param parser JSON 字段解析器，当前值必须为指定格式字符串
     * @param context Jackson 反序列化上下文，用于返回统一的字段格式异常
     * @return 严格解析后的失效时间
     * @throws IOException 输入不是合法日期时间字符串时抛出
     */
    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null) {
            throw context.weirdStringException(null, LocalDateTime.class,
                    "失效时间格式必须为 " + DATE_TIME_PATTERN);
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw context.weirdStringException(value, LocalDateTime.class,
                    "失效时间格式必须为 " + DATE_TIME_PATTERN);
        }
    }
}
