package io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/** 通用会话复核请求；包含原始访问凭据时禁止记录、序列化到日志或生成 toString。 */
@Getter
@Setter
public class OAuth2SessionInspectionReqDTO {
    /** 主体种类，仅 USER（后台真实用户）或 MACHINE（客户端凭据机器）。 */
    private String subjectType;
    /** 原始访问令牌，不含 Bearer 前缀；与 sessionId 严格二选一，不接受 refresh_token。 */
    private String accessToken;
    /** 已建立可信关联的访问会话记录 ID；不能独立作为查询授权。 */
    private Long sessionId;
    /** 必填预期租户，须位于服务调用者允许的租户集合并与主体记录相符。 */
    private Long expectedTenantId;
    /** 会话 ID 查询必填；真实用户为正数，机器为 0。 */
    private Long expectedUserId;
    /** 机器会话 ID 查询必填的预期客户端实体主键，禁止同名重建替换。 */
    private Long expectedClientRecordId;
    /** 按需复核的当前 token/client scope，须由调用者策略允许。 */
    private String requiredScope;
    /** 按需复核的当前客户端资源，须由调用者策略允许。 */
    private String requiredResource;
    /** 按需检查的功能权限，最多 16 项，须在调用者策略白名单内；不返回权限全集。 */
    private List<String> requiredPermissions = new ArrayList<>();
    /** 按需复核平台管理员；仅 USER 且消费者单独获准时可用，不接受调用方自报管理员结果。 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean requirePlatformAdministrator;
}
