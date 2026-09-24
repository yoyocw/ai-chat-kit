package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

/**
 * 百炼群聊工作流输出契约，要求 output.text 为包含 replies 数组的 JSON。
 */
@Data
public class BailianGroupOutput {

    /** 按工作流决定顺序排列的执行智能体回复，最后一条必须是 ORCHESTRATOR 汇总。 */
    private List<BailianGroupReply> replies;
    /** 地图、来源或业务卡片等可选结构化扩展结果。 */
    private JsonNode responseData;
}
