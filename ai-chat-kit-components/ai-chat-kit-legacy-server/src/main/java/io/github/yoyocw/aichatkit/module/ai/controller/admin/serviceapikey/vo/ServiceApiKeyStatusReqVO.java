package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import io.github.yoyocw.aichatkit.compat.framework.common.validation.InEnum;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.CommonStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 管理后台修改 MCP 第三方调用方启停状态请求。
 */
@Schema(description = "管理后台 - 修改 MCP 第三方调用方状态请求")
@Data
public class ServiceApiKeyStatusReqVO {

    /** 调用方主键编号。 */
    @Schema(description = "调用方编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "调用方编号不能为空")
    private Long id;

    /** 目标状态，0 启用、1 停用。 */
    @Schema(description = "状态，0 启用、1 停用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    @InEnum(CommonStatusEnum.class)
    private Integer status;
}
