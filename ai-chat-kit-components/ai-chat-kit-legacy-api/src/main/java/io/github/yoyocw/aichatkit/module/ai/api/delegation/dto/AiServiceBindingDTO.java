package io.github.yoyocw.aichatkit.module.ai.api.delegation.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 管理员维护的服务接入边界，不接收模型或对话用户提供的绑定。 */
@Getter
@Setter
public class AiServiceBindingDTO {
    /** 接入业务系统标识，1 至 64 位字母、数字、下划线或短横线。 */
    private String businessSystem;
    /** 部署环境标识，1 至 64 位字母、数字、下划线或短横线。 */
    private String environment;
    /** 该专用客户端唯一允许的租户，非负；不同租户使用不同客户端。 */
    private Long tenantId;
    /** 已批准的百炼应用 ID，1 至 10 个，不重复。 */
    private List<String> appIds;
    /** 已批准的用户级插件 ID，0 至 10 个；空列表表示不允许插件凭据透传。 */
    private List<String> userAuthToolIds;
    /** 已批准的原生 MCP ID，0 至 10 个；空列表表示不允许 MCP 凭据透传。 */
    private List<String> mcpIds;
}
