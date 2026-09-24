package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台 MCP 第三方调用方分页请求。
 */
@Schema(description = "管理后台 - MCP 第三方调用方分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceApiKeyPageReqVO extends PageParam {

    /** 按调用方编码模糊筛选。 */
    @Schema(description = "调用方编码")
    private String clientCode;

    /** 按调用方名称模糊筛选。 */
    @Schema(description = "调用方名称")
    private String clientName;

    /** 按启停状态筛选，0 启用、1 停用。 */
    @Schema(description = "状态，0 启用、1 停用", example = "0")
    private Integer status;
}
