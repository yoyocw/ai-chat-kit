package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 百炼应用调用用量汇总，只承载官方 usage.models 返回的模型和 Token 计量数据。
 */
@Data
public class BailianUsage {

    /** 本轮调用涉及的模型 ID，多个模型按英文逗号连接。 */
    private String modelNames;
    /** 本轮所有模型合计输入 Token 数。 */
    private Integer inputTokens;
    /** 本轮所有模型合计输出 Token 数。 */
    private Integer outputTokens;

    /**
     * 从百炼官方 usage.models 结构汇总模型和 Token 数据。
     *
     * @param usageNode 百炼 SSE 帧中的 usage 节点
     * @return 当前帧的用量汇总；未返回 models 时返回 null
     */
    public static BailianUsage from(JsonNode usageNode) {
        JsonNode modelsNode = usageNode.path("models");
        if (!modelsNode.isArray() || modelsNode.size() == 0) {
            return null;
        }
        Set<String> modelNames = new LinkedHashSet<String>();
        long inputTokens = 0L;
        long outputTokens = 0L;
        for (JsonNode modelNode : modelsNode) {
            String modelId = sanitizeModelId(modelNode.path("model_id").asText(""));
            if (StringUtils.hasText(modelId)) {
                modelNames.add(modelId);
            }
            inputTokens += Math.max(0, modelNode.path("input_tokens").asInt(0));
            outputTokens += Math.max(0, modelNode.path("output_tokens").asInt(0));
        }
        BailianUsage usage = new BailianUsage();
        String joinedModelNames = String.join(",", modelNames);
        usage.setModelNames(joinedModelNames.length() <= 500
                ? joinedModelNames : joinedModelNames.substring(0, 500));
        usage.setInputTokens((int) Math.min(inputTokens, Integer.MAX_VALUE));
        usage.setOutputTokens((int) Math.min(outputTokens, Integer.MAX_VALUE));
        return usage;
    }

    /** 清理外部模型 ID 并限制单个 ID 长度，防止污染审计字段。 */
    private static String sanitizeModelId(String value) {
        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        return sanitized.length() <= 100 ? sanitized : sanitized.substring(0, 100);
    }
}
