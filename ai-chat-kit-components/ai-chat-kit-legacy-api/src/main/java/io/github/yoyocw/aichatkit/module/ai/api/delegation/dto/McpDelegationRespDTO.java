package io.github.yoyocw.aichatkit.module.ai.api.delegation.dto;

import lombok.Getter;
import lombok.Setter;

/** 本轮委托凭据；禁止记录或持久化，刻意不生成包含凭据的 toString。 */
@Getter
@Setter
public class McpDelegationRespDTO {
    /** 由原始访问令牌确认的后台用户编号。 */
    private Long userId;
    /** 用户实际租户编号，调用方必须与对话所属租户比对。 */
    private Long tenantId;
    /** 带 mcp_jwt_ 前缀的短期凭据，只传递给允许的工具。 */
    private String credential;
}
