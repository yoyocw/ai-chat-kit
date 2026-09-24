package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 群聊候选智能体响应，用于成员选择和消息发言人展示。
 */
@Schema(description = "管理后台 - AI 群聊候选智能体响应")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiGroupChatAgentRespVO implements io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMember {

    /** 百炼工作流识别的稳定智能体编码。 */
    @Schema(description = "智能体编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "FOREST_MAP")
    private String code;
    /** 智能体展示名称。 */
    @Schema(description = "智能体名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "林业问图智能体")
    private String name;
    /** 智能体在群聊中的职责说明。 */
    @Schema(description = "智能体职责", requiredMode = Schema.RequiredMode.REQUIRED, example = "空间与资源查询")
    private String role;
}
