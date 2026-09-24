package io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息响应，覆盖文本、生成状态与百炼扩展业务结果。
 */
@Schema(description = "管理后台 - AI 对话消息响应")
@Data
public class AiChatMessageRespVO {

    /** 消息主键编号。 */
    @Schema(description = "消息编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    /** 所属会话编号。 */
    @Schema(description = "会话编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conversationId;
    /** 消息角色，仅为 user 或 assistant。 */
    @Schema(description = "消息角色", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
    /** 用户问题或模型回复正文。 */
    @Schema(description = "消息正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
    /** 是否请求地图业务结果。 */
    @Schema(description = "是否开启地图业务结果")
    private Boolean mapEnabled;
    /** 生成状态：0 生成中、1 已完成、2 已停止、3 失败。 */
    @Schema(description = "生成状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
    /** 百炼请求编号，用于平台侧问题追踪。 */
    @Schema(description = "百炼请求编号")
    private String requestId;
    /** 服务端白名单构造的版本化扩展结果 JSON；旧记录可能缺少协议版本。 */
    @Schema(description = "版本化扩展响应 JSON")
    private String responseData;
    /** 生成失败原因，成功时为空。 */
    @Schema(description = "生成失败原因")
    private String errorMessage;
    /** 消息创建时间。 */
    @Schema(description = "消息创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
}
