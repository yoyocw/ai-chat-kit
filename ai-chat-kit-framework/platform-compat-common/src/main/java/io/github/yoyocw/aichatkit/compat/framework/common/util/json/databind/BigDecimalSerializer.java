package io.github.yoyocw.aichatkit.compat.framework.common.util.json.databind;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * BigDecimal 序列化规则
 *
 * 去除小数末尾多余的 0，使数值更简洁：整数 13.00 序列化为 13，小数 13.20 序列化为 13.2。
 * 使用 toPlainString 输出，避免 stripTrailingZeros 对 600 等整数产生科学计数法（6E+2）。
 *
 * @author 超哥
 */
public class BigDecimalSerializer extends JsonSerializer<BigDecimal> {

    public static final BigDecimalSerializer INSTANCE = new BigDecimalSerializer();

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // 去除末尾多余的 0；toPlainString 避免科学计数法
        gen.writeNumber(value.stripTrailingZeros().toPlainString());
    }

}
