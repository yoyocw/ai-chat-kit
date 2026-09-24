package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.user.dto;

import lombok.Data;

/**
 * 服务身份绑定的后台用户信息。
 *
 * <p>用于安全组件在没有普通 OAuth2 访问令牌的情况下，校验固定服务账号并构造登录上下文。</p>
 */
@Data
public class ServiceUserRespDTO {

    /** 后台用户主键编号，必须为正数。 */
    private Long id;

    /** 用户所属租户编号，必须与服务秘钥配置的固定租户一致。 */
    private Long tenantId;

    /** 用户状态，取值见 CommonStatusEnum；仅启用状态允许建立服务身份。 */
    private Integer status;

    /** 用户昵称，仅用于登录上下文和审计展示，不参与身份判断。 */
    private String nickname;

    /** 用户所属部门编号，用于解析角色部门数据范围。 */
    private Long deptId;

}
