package io.github.yoyocw.aichatkit.compat.framework.security.core.util;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.UserTypeEnum;
import io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2.OAuth2SessionInspectionClient;
import java.time.Instant;
import java.util.Objects;

/** MCP 用户会话复核只通过通用受限查询；未配置时拒绝用户 JWT，不影响固定密钥认证。 */
public class McpUserSessionValidator {
    /** 无缓存的通用认证客户端；null 只表示宿主未启用用户会话核验。 */
    private final OAuth2SessionInspectionClient inspectionClient;

    /** 创建未启用检查器；遇到用户 JWT 时明确拒绝，绝不按裸会话 ID 查询。 */
    public McpUserSessionValidator() {
        this.inspectionClient = null;
    }

    /** @param inspectionClient 宿主已配置固定源站和消费者身份的受限客户端，不得为 null */
    public McpUserSessionValidator(OAuth2SessionInspectionClient inspectionClient) {
        this.inspectionClient = Objects.requireNonNull(inspectionClient);
    }

    /**
     * 复核已经本地验签的用户身份和期限；通用客户端校验会话、用户、租户三元组一致性。
     * @param sessionId 已验签的访问会话编号，必须为正数
     * @param userId 已验签的真实用户编号
     * @param tenantId 已验签的租户编号
     * @param jwtExpiresAt JWT 到期 UTC 秒，必填且不得超过当前登录会话
     * @throws IllegalStateException 未配置检查器、远程查询或协议失败，禁止降级
     * @throws IllegalArgumentException 用户类型、授权或有效期不合法
     */
    public void validate(Long sessionId, Long userId, Long tenantId, Long jwtExpiresAt) {
        if (inspectionClient == null) { throw new IllegalStateException("MCP 用户会话核验未启用"); }
        if (jwtExpiresAt == null || jwtExpiresAt <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("MCP JWT 已过期或缺少有效期");
        }
        OAuth2SessionInspectionReqDTO request = new OAuth2SessionInspectionReqDTO();
        request.setSubjectType("USER");
        request.setSessionId(sessionId);
        request.setExpectedUserId(userId);
        request.setExpectedTenantId(tenantId);
        OAuth2SessionInspectionRespDTO identity = inspectionClient.inspect(request);
        if (!UserTypeEnum.ADMIN.getValue().equals(identity.getUserType()) || !identity.isPermissionsSatisfied()) {
            throw new IllegalArgumentException("MCP 登录会话已失效或身份不匹配");
        }
        if (jwtExpiresAt <= Instant.now().getEpochSecond()
                || jwtExpiresAt > Math.floorDiv(identity.getExpiresAtMillis(), 1000L)) {
            throw new IllegalArgumentException("MCP JWT 超出登录会话有效期");
        }
    }
}
