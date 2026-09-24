package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Set;

/**
 * AI 群聊成员调整请求，生成期间禁止修改候选成员。
 */
@Schema(description = "管理后台 - AI 群聊成员调整请求")
@Data
public class AiGroupChatMemberUpdateReqVO {

    /** 需要调整成员的群聊会话编号。 */
    @Schema(description = "群聊会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "群聊会话编号不能为空")
    private Long id;
    /** 调整后的候选智能体编码集合。 */
    @Schema(description = "候选智能体编码，成员数量必须为 2 至 3 个", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "群聊成员不能为空")
    @Size(min = 2, max = 3, message = "群聊成员数量必须为 2 至 3 个")
    private Set<@NotBlank(message = "智能体编码不能为空")
            @Size(max = 64, message = "智能体编码不能超过 64 个字符") String> memberCodes;
}
