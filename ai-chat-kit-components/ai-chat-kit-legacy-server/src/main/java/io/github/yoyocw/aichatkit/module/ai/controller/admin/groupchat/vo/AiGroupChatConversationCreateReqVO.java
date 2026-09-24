package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Set;

/**
 * AI 群聊创建请求，标题可省略，成员必须为 2 至 3 个不重复的合法智能体编码。
 */
@Schema(description = "管理后台 - AI 群聊创建请求")
@Data
public class AiGroupChatConversationCreateReqVO {

    /** 群聊标题；为空时服务端根据前两个成员自动生成。 */
    @Schema(description = "群聊标题，为空时自动生成", example = "B-023 综合研判小组")
    @Size(max = 30, message = "群聊标题不能超过 30 个字符")
    private String title;
    /** 候选智能体编码集合，工作流只能从这些成员中选择实际发言人。 */
    @Schema(description = "候选智能体编码，成员数量必须为 2 至 3 个", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "群聊成员不能为空")
    @Size(min = 2, max = 3, message = "群聊成员数量必须为 2 至 3 个")
    private Set<@NotBlank(message = "智能体编码不能为空")
            @Size(max = 64, message = "智能体编码不能超过 64 个字符") String> memberCodes;
}
