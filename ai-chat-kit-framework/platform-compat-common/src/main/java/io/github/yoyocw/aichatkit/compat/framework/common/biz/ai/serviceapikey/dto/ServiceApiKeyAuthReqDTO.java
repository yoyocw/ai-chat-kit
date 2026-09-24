package io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * MCP 服务密钥摘要鉴权请求。
 */
@Data
public class ServiceApiKeyAuthReqDTO {

    /** 完整 {@code mcp_} 原始密钥的 SHA-256 小写十六进制摘要，固定 64 个字符。 */
    @NotBlank(message = "密钥摘要不能为空")
    @Pattern(regexp = "^[0-9a-f]{64}$", message = "密钥摘要格式不正确")
    private String keySha256;
}
