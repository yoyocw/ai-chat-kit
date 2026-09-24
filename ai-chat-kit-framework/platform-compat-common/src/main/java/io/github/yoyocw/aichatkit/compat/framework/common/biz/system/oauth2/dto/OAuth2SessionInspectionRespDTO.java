package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 通用会话复核的最小身份快照，不包含凭据、完整授权清单或客户端附加信息。 */
@Getter
@Setter
public class OAuth2SessionInspectionRespDTO {
    /** 已复核存在且有效的访问会话主键。 */
    private Long sessionId;
    /** 真实用户编号或机器标识 0。 */
    private Long userId;
    /** 后台用户类型编码，沿用系统 UserTypeEnum。 */
    private Integer userType;
    /** 从持久化会话确定的租户编号。 */
    private Long tenantId;
    /** 当前会话所属客户端标识。 */
    private String clientId;
    /** 当前数据库客户端实体主键；机器会话还须匹配原签发绑定。 */
    private Long clientRecordId;
    /** 原访问会话到期 UTC epoch 毫秒，由认证服务按其数据库时间语义转换，查询不会续期。 */
    private Long expiresAtMillis;
    /** 本次请求的白名单权限是否全部满足；未请求权限时为 true。 */
    private boolean permissionsSatisfied;
    /** 受控平台管理员核验结果；未请求时为 null 且不输出，不能用缺字段推断为已授权。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean platformAdministrator;
}
