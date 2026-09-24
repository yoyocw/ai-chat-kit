package io.github.yoyocw.aichatkit.module.ai.service.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.security.config.McpServiceJwtProperties;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.config.AiKeyManagementProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiLocalDelegationSigningProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.McpJwtPublicKeyRespVO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.serviceapikey.AiManagedSigningKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** AI 自有密钥首次初始化；管理员资格由通用认证服务裁决，不读取 system 用户或租户表。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.key-management", name = "enabled", havingValue = "true")
public class AiManagedSigningKeyService {
    /** 显式候选开关，关闭后不能通过已有 Bean 引用继续初始化。 */
    private final AiKeyManagementProperties management;
    /** 秘密注入签发模式与数据库首次初始化互斥，防止生成另一套未使用密钥。 */
    private final AiLocalDelegationSigningProperties signing;
    /** 仅部署配置可选择初始化的 issuer/audience。 */
    private final McpServiceJwtProperties jwt;
    /** 主体租户由宿主部署配置确定，不能由管理请求指定。 */
    private final AiSessionInspectionProperties inspection;
    /** 校验真实访问会话、客户端实体和当前平台管理员资格。 */
    private final AiSessionInspectionClient inspector;
    /** AI 数据源的原子首次插入，不覆盖既有或停用记录。 */
    private final AiManagedSigningKeyMapper keyMapper;

    /**
     * 为已验证的平台管理员首次生成 RSA 3072 密钥，仅返回公开信息。
     * @param authorization 当前真实用户 Bearer 访问令牌，不采信透传登录身份
     * @return 公开签发方、接收方及公钥；私钥只用于本次数据库写入
     * @throws IllegalStateException 非平台管理员、配置不符、已有记录或存储失败，异常不携带敏感原因
     */
    public McpJwtPublicKeyRespVO initialize(String authorization) {
        try {
            validateConfiguration();
            verifyAdministrator(authorization);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair pair = generator.generateKeyPair();
            String publicKey = pem("PUBLIC KEY", pair.getPublic().getEncoded());
            // 唯一约束阻止并发初始化及覆盖停用记录，禁止通过已有密钥查询向调用方泄露私钥。
            int inserted = keyMapper.initialize(jwt.getIssuer(), jwt.getAudience(),
                    pem("PRIVATE KEY", pair.getPrivate().getEncoded()), publicKey);
            if (inserted != 1) { throw failure(); }
            return new McpJwtPublicKeyRespVO(jwt.getIssuer(), jwt.getAudience(), publicKey);
        } catch (Exception ex) {
            // SQL 驱动异常可能包含绑定参数，不交给通用异常处理或错误日志。
            throw failure();
        }
    }

    /** 参数中的身份只作为查询对象，平台资格必须由受限认证接口返回明确的 true。 */
    private void verifyAdministrator(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 4103) {
            throw failure();
        }
        OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
        query.setSubjectType("USER");
        query.setAccessToken(authorization.substring(7));
        query.setExpectedTenantId(inspection.getSubjectTenantId());
        query.setRequirePlatformAdministrator(true);
        OAuth2SessionInspectionRespDTO identity = inspector.inspect(query);
        if (!Boolean.TRUE.equals(identity.getPlatformAdministrator())) { throw failure(); }
    }

    /** 不自动改变现有签发模式、密钥或信任标识，不允许请求决定初始化目标。 */
    private void validateConfiguration() {
        if (!management.isEnabled() || !inspection.isEnabled() || signing.isEnabled()
                || !validIdentifier(jwt.getIssuer()) || !validIdentifier(jwt.getAudience())) { throw failure(); }
    }

    /** @param value 部署信任标识 @return 是否非空且不超过既有表字段长度 */
    private boolean validIdentifier(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 128;
    }

    /** @param type 标准密钥编码种类 @param encoded JCA 编码 @return 仅用于本次初始化的 PEM */
    private String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }

    /** @return 固定脱敏异常，拒绝时不返回数据库、凭据、角色或密钥内容 */
    private IllegalStateException failure() {
        return new IllegalStateException("AI 密钥初始化未完成，请核对管理权限、配置及密钥是否已存在");
    }
}
