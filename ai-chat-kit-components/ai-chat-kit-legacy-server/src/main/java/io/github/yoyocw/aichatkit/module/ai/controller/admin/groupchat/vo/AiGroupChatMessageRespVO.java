package io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 群聊消息响应，用于还原用户与多个智能体按编排顺序的历史消息。
 */
@Schema(description = "管理后台 - AI 群聊消息响应")
@Data
public class AiGroupChatMessageRespVO {

    /** 群聊消息主键编号。 */
    @Schema(description = "消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    /** 所属群聊会话编号。 */
    @Schema(description = "群聊会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conversationId;
    /** 消息角色：user 或 assistant。 */
    @Schema(description = "消息角色", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
    /** 实际发言智能体编码，用户消息为空。 */
    @Schema(description = "发言智能体编码")
    private String speakerCode;
    /** 实际发言智能体名称，用户消息为空。 */
    @Schema(description = "发言智能体名称")
    private String speakerName;
    /** 本轮工作流中的发言顺序，用户消息为空。 */
    @Schema(description = "发言轮次")
    private Integer roundNo;
    /** 用户问题或智能体回复正文。 */
    @Schema(description = "消息正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
    /** 生成状态：0生成中、1已完成、2已停止、3失败。 */
    @Schema(description = "生成状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
    /** 百炼请求编号，用于问题追踪。 */
    @Schema(description = "百炼请求编号")
    private String requestId;
    /** 服务端白名单构造的版本化群聊扩展结果 JSON，仅首条智能体回复可能包含。 */
    @Schema(description = "版本化群聊扩展响应 JSON")
    private String responseData;
    /** 生成失败原因，成功时为空。 */
    @Schema(description = "失败原因")
    private String errorMessage;
    /** 消息创建时间。 */
    @Schema(description = "消息创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
}
