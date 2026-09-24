package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * RPC 服务 - 对话地图任务匹配请求 DTO。
 */
@Schema(description = "RPC 服务 - 对话地图任务匹配请求 DTO")
@Data
public class DashboardMapMatchReqDTO {

    /** 用户本轮问题，用于匹配明确出现的任务业务标识，最大 10000 字符。 */
    @Schema(description = "用户本轮问题", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户问题不能为空")
    @Size(max = 10000, message = "用户问题不能超过 10000 个字符")
    private String question;
}
