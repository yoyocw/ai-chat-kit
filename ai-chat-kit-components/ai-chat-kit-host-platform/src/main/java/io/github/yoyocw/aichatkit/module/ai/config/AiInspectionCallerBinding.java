package io.github.yoyocw.aichatkit.module.ai.config;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO;
import lombok.Getter;
import lombok.Setter;

/** AI 自有接入配置，以真实客户端实体和业务编号共同绑定，防止删除后同名重建继承权限。 */
@Getter
@Setter
public class AiInspectionCallerBinding {
    /** 认证侧客户端数据库实体编号，必须为正数。 */
    private Long clientRecordId;
    /** 认证侧客户端业务编号，必须与实时会话结果完全一致。 */
    private String clientId;
    /** AI 管理员批准的系统、环境、租户和应用/工具允许列表。 */
    private AiServiceBindingDTO binding;
}
