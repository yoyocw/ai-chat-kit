package io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 管理后台 MCP 第三方调用方响应，不暴露当前或上一密钥摘要。
 */
@Schema(description = "管理后台 - MCP 第三方调用方响应")
@Data
public class ServiceApiKeyRespVO {

    /** 调用方主键编号。 */
    private Long id;
    /** 租户内稳定调用方编码。 */
    private String clientCode;
    /** 调用方显示名称。 */
    private String clientName;
    /** 绑定服务账号用户编号。 */
    private Long serviceUserId;
    /** 固定租户编号。 */
    private Long tenantId;
    /** 单次分页最大返回条数。 */
    private Integer maxPageSize;
    /** 调用方自动失效时间，为空表示长期有效。 */
    private LocalDateTime expireTime;
    /** 启停状态，0 启用、1 停用。 */
    private Integer status;
    /** 是否仍保留轮换前的上一把密钥。 */
    private Boolean previousKeyActive;
    /** 当前调用方被数据库授权的稳定 MCP 端点编码集合。 */
    private Set<String> endpointCodes = new LinkedHashSet<>();
    /** 记录创建时间。 */
    private LocalDateTime createTime;
    /** 记录最后更新时间。 */
    private LocalDateTime updateTime;
}
