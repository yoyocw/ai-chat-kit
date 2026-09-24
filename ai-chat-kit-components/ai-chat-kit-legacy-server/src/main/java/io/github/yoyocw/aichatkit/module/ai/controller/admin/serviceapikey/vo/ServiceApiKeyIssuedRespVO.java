package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * MCP 密钥创建或轮换响应，原始密钥只在本次响应中出现。
 */
@Schema(description = "管理后台 - MCP 密钥一次性签发响应")
@Data
@AllArgsConstructor
public class ServiceApiKeyIssuedRespVO {

    /** 调用方主键编号。 */
    private Long id;

    /** 完整原始密钥，必须立即保存到第三方平台且不得写入日志。 */
    private String token;

    /** 完整原始密钥的 SHA-256 摘要，仅用于管理员核对。 */
    private String sha256;
}
