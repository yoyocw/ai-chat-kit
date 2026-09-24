package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 管理后台创建 MCP 第三方调用方请求。
 */
@Schema(description = "管理后台 - 创建 MCP 第三方调用方请求")
@Data
public class ServiceApiKeyCreateReqVO {

    /** 租户内唯一调用方编码，创建后不允许修改。 */
    @Schema(description = "调用方编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "bailian-platform")
    @NotBlank(message = "调用方编码不能为空")
    @Size(max = 64, message = "调用方编码不能超过 64 个字符")
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*$", message = "调用方编码只能包含小写字母、数字、短横线和下划线")
    @JsonProperty("client_code")
    @JsonAlias("clientCode")
    private String clientCode;

    /** 调用方展示名称，例如平台名加应用名。 */
    @Schema(description = "调用方名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "百炼林业智能体")
    @NotBlank(message = "调用方名称不能为空")
    @Size(max = 128, message = "调用方名称不能超过 128 个字符")
    @JsonProperty("client_name")
    @JsonAlias("clientName")
    private String clientName;

    /** 绑定的后台只读服务账号编号，必须属于当前租户且处于启用状态。 */
    @Schema(description = "服务账号用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "服务账号用户编号不能为空")
    @Min(value = 1, message = "服务账号用户编号必须为正数")
    @JsonProperty("service_user_id")
    @JsonAlias("serviceUserId")
    private Long serviceUserId;

    /** 单次分页最大返回条数，默认 50，允许范围 1 至 200。 */
    @Schema(description = "单次分页上限", requiredMode = Schema.RequiredMode.REQUIRED, example = "50")
    @NotNull(message = "单次分页上限不能为空")
    @Min(value = 1, message = "单次分页上限不能小于 1")
    @Max(value = 200, message = "单次分页上限不能大于 200")
    @JsonProperty("max_page_size")
    @JsonAlias("maxPageSize")
    private Integer maxPageSize = 50;

    /** 首次分配给调用方的稳定 MCP 端点编码集合，至少一项且自动去重。 */
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

    /** 调用方自动失效时间，入参格式固定为 yyyy-MM-dd HH:mm:ss，为空表示长期有效。 */
    @Schema(description = "失效时间，格式为 yyyy-MM-dd HH:mm:ss", example = "2099-12-31 23:59:59")
    @JsonProperty("expire_time")
    @JsonAlias("expireTime")
    @JsonDeserialize(using = ServiceApiKeyExpireTimeDeserializer.class)
    private LocalDateTime expireTime;
}
