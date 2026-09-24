package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import lombok.Data;

/**
 * 百炼群聊工作流单个智能体回复，数组顺序即工作流决定的发言顺序。
 */
@Data
public class BailianGroupReply {

    /** 实际发言智能体稳定编码，必须属于群成员；最终汇总允许使用 ORCHESTRATOR。 */
    private String speakerCode;
    /** 智能体回复正文，必须非空。 */
    private String content;
}
