package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Set;

/**
 * 管理后台更新 MCP 调用方端点权限请求。
 */
@Schema(description = "管理后台 - 更新 MCP 调用方端点权限请求")
@Data
public class ServiceApiKeyEndpointUpdateReqVO {

    /** 当前租户内需要更新授权的调用方主键编号。 */
    @Schema(description = "调用方编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "调用方编号不能为空")
    @Min(value = 1, message = "调用方编号必须为正数")
    private Long id;

    /** 替换后的稳定 MCP 端点编码集合，至少一项且自动去重。 */
    @Schema(description = "允许访问的 MCP 端点编码集合", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[\"forest.monitor.task.get\", \"forest.monitor.task.page\"]")
    @NotEmpty(message = "MCP 端点编码集合不能为空")
    @Size(max = 100, message = "单个调用方最多配置 100 个 MCP 端点")
    @JsonProperty("endpoint_codes")
    @JsonAlias("endpointCodes")
    private Set<@NotBlank(message = "MCP 端点编码不能为空")
            @Size(max = 128, message = "MCP 端点编码不能超过 128 个字符")
            @Pattern(regexp = "^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*){1,7}$",
                    message = "MCP 端点编码格式无效") String> endpointCodes;
}
