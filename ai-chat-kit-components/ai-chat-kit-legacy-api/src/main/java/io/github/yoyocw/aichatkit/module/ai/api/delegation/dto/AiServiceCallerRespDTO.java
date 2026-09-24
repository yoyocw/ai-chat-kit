package io.github.yoyocw.aichatkit.module.ai.api.delegation.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 认证侧核验的 AI 服务调用身份，不包含用户委托或原始客户端凭据。 */
@Getter
@Setter
public class AiServiceCallerRespDTO {
    /** 已启用且允许访问 AI 资源的 OAuth2 客户端编号。 */
    private String clientId;
    /** 当前核验客户端实体的数据库主键；不代表旧令牌已绑定实体代次。 */
    private Long clientRecordId;
    /** 服务访问令牌所属租户；接入入口还须与用户委托的租户核对。 */
    private Long tenantId;
    /** 服务访问令牌到期时间；不能据此延长用户委托期限。 */
    private LocalDateTime expiresTime;
    /** 数据库当前批准的系统、环境、租户和目标绑定；下游必须逐项匹配后才传递凭据。 */
    private AiServiceBindingDTO binding;
}
