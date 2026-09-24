package io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * MCP 服务密钥鉴权结果，不包含原始密钥或任何已存密钥摘要。
 */
@Data
public class ServiceApiKeyAuthRespDTO {

    /** 调用方数据库主键，用于安全审计关联。 */
    private Long clientId;

    /** 调用方稳定业务编码，例如 bailian-platform。 */
    private String clientCode;

    /** 调用方显示名称，用于审计展示。 */
    private String clientName;

    /** 绑定的后台只读服务用户编号。 */
    private Long serviceUserId;

    /** 服务用户所属且调用方固定绑定的租户编号。 */
    private Long tenantId;

    /** 单次分页最大条数，取值范围 1 至 200。 */
    private Integer maxPageSize;

    /** 服务用户昵称，仅用于登录上下文和审计展示。 */
    private String nickname;

    /** 服务用户部门编号，仅用于登录上下文。 */
    private Long deptId;

    /** JWT 的登录令牌到期时刻，UTC Unix 秒；固定密钥认证不使用，保持 null。 */
    @JsonProperty("exp")
    private Long expiresAtEpochSecond;

    /** 用户 JWT 绑定的持久化访问令牌记录 ID，必须为正数；固定密钥认证不使用。 */
    private Long accessTokenId;
    /** 委托 AI 入口允许的机器客户端编号；普通工具 JWT 和固定密钥为空。 */
    private String delegationClientId;
    /** 委托入口所属业务系统，必须与服务客户端当前绑定一致。 */
    private String delegationBusinessSystem;
    /** 委托入口部署环境，禁止跨环境使用；普通工具 JWT 为空。 */
    private String delegationEnvironment;

    /** 固定密钥 B1 数据范围的允许创建人集合，必须非空；用户 JWT 不使用此集合。 */
    private Set<Long> allowedCreatorIds = new HashSet<>();

    /** 数据库分配给当前调用方的稳定 MCP 端点编码集合；空集合表示无接口权限。 */
    private Set<String> allowedEndpointCodes = new HashSet<>();
}
