package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.McpDelegationRespDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceCallerRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.security.config.McpServiceJwtProperties;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.McpServiceJwtCodec;
import io.github.yoyocw.aichatkit.module.ai.config.AiLocalDelegationSigningProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.service.serviceapikey.AiSigningKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;

/** AI 自有用户委托签发，只通过受限认证接口证明身份，不读 system 密钥或 OAuth2 数据表。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.local-delegation-signing", name = "enabled", havingValue = "true")
public class AiLocalDelegationSigningService {
    /** 真实后台用户会话及固定权限复核；网络或身份异常不能作为权限不足继续尝试。 */
    private final AiSessionInspectionClient client;
    /** 当前部署唯一主体租户。 */
    private final AiSessionInspectionProperties inspection;
    /** 由秘密配置注入原私钥，禁止自动初始化或覆盖密钥。 */
    private final AiLocalDelegationSigningProperties signing;
    /** 明确选择秘密配置或 AI 自有数据库；缺失、停用、异常不回退其他来源。 */
    private final AiSigningKeyProvider keyProvider;
    /** 复用现有 issuer、audience、端点范围和分页上限。 */
    private final McpServiceJwtProperties jwt;
    /** 复用既有固定头部和 RS256 协议。 */
    private final McpServiceJwtCodec codec;
    /** 代理机器及模式权限复核，签发完成前绑定当前 AI 接入身份。 */
    private final AiInspectedDelegationService delegationValidator;

    /**
     * 签发仅供当前代理机器及固定模式使用的用户证明，沿用普通签发的实时身份规则。
     * @param machineAuthorization 本轮临时机器 Bearer 令牌
     * @param userAuthorization 当前真实用户 Bearer 令牌
     * @param mode 控制器固定的 single 或 group
     * @return 绑定机器客户端、系统和环境的受限委托
     * @throws IllegalStateException 跨租户、配置、权限或签名无效，无旧签发回退
     */
    public McpDelegationRespDTO issueForCaller(String machineAuthorization, String userAuthorization, String mode) {
        try {
            AiServiceCallerRespDTO caller = delegationValidator.validateCaller(machineAuthorization);
            McpDelegationRespDTO result = issue(userAuthorization);
            if (!Objects.equals(caller.getTenantId(), result.getTenantId())) { throw failure(); }
            ServiceApiKeyAuthRespDTO identity = codec.verify(result.getCredential());
            identity.setDelegationClientId(caller.getClientId());
            identity.setDelegationBusinessSystem(caller.getBinding().getBusinessSystem());
            identity.setDelegationEnvironment(caller.getBinding().getEnvironment());
            String credential = codec.sign(identity, localTime(Instant.ofEpochSecond(identity.getExpiresAtEpochSecond())),
                    keyProvider.getPrivateKey());
            // 输出前核验具体发送模式，普通 issue 的 OR 权限不能越过单聊或群聊入口权限。
            delegationValidator.validate(machineAuthorization, credential, mode);
            result.setCredential(credential);
            return result;
        } catch (RuntimeException ex) { throw failure(); }
    }

    /** 启用前使用仅在内存中的合成挑战签验，验证私钥、公钥及协议配置一致；不输出挑战凭据。 */
    @PostConstruct
    public void validateKeyPair() {
        try {
            if (!inspection.isEnabled() || !signing.isEnabled()) { throw failure(); }
            ServiceApiKeyAuthRespDTO challenge = identity(1L, 1L, 0L);
            codec.verify(codec.sign(challenge, localTime(Instant.now().plusSeconds(30)), keyProvider.getPrivateKey()));
        } catch (RuntimeException ex) { throw failure(); }
    }

    /**
     * 为普通对话入口签发工具委托，权限保持既有单聊或群聊任一满足的语义。
     * @param authorization 当前真实后台用户 Bearer 登录令牌，只同步用于受限复核
     * @return 与真实用户会话同身份、同到期上限的工具凭据，不含私钥
     * @throws IllegalStateException 身份、权限、签名或配置无效；不回退认证侧签发
     */
    public McpDelegationRespDTO issue(String authorization) {
        try {
            if (!signing.isEnabled() || authorization == null || !authorization.startsWith("Bearer ")) {
                throw failure();
            }
            OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
            query.setSubjectType("USER");
            query.setAccessToken(authorization.substring(7));
            query.setExpectedTenantId(inspection.getSubjectTenantId());
            query.setRequiredPermissions(Collections.singletonList("ai:chat:seed"));
            OAuth2SessionInspectionRespDTO user = client.inspectWithPermissionResult(query);
            // 仅明确的权限不足允许检查另一个固定权限；异常立即由外层拒绝。
            if (!user.isPermissionsSatisfied()) {
                query.setAccessToken(null);
                query.setSessionId(user.getSessionId());
                query.setExpectedUserId(user.getUserId());
                query.setExpectedClientRecordId(user.getClientRecordId());
                query.setRequiredPermissions(Collections.singletonList("aigroup:chat:message:send"));
                user = client.inspect(query);
            }
            ServiceApiKeyAuthRespDTO identity = identity(user.getSessionId(), user.getUserId(), user.getTenantId());
            String credential = codec.sign(identity, localTime(Instant.ofEpochMilli(user.getExpiresAtMillis())),
                    keyProvider.getPrivateKey());
            // 每次签发后也验签，避免运行中秘密配置变化造成签发与当前信任公钥脱节。
            ServiceApiKeyAuthRespDTO verified = codec.verify(credential);
            if (!Objects.equals(user.getSessionId(), verified.getAccessTokenId())
                    || !Objects.equals(user.getUserId(), verified.getServiceUserId())
                    || !Objects.equals(user.getTenantId(), verified.getTenantId())) { throw failure(); }
            McpDelegationRespDTO result = new McpDelegationRespDTO();
            result.setUserId(user.getUserId());
            result.setTenantId(user.getTenantId());
            result.setCredential(credential);
            return result;
        } catch (RuntimeException ex) { throw failure(); }
    }

    /** 范围来自部署配置，调用方不能指定用户、有效期或只读端点。 */
    private ServiceApiKeyAuthRespDTO identity(Long sessionId, Long userId, Long tenantId) {
        if (jwt.getAllowedEndpointCodes() == null || jwt.getAllowedEndpointCodes().isEmpty()) { throw failure(); }
        ServiceApiKeyAuthRespDTO identity = new ServiceApiKeyAuthRespDTO();
        identity.setAccessTokenId(sessionId);
        identity.setServiceUserId(userId);
        identity.setTenantId(tenantId);
        identity.setClientCode("ai-bailian");
        identity.setClientName("百炼智能体短期调用");
        identity.setMaxPageSize(jwt.getMaxPageSize());
        identity.setAllowedEndpointCodes(new LinkedHashSet<>(jwt.getAllowedEndpointCodes()));
        return identity;
    }

    /** 仅适配既有 codec 的本地时间参数，源会话时刻始终使用 UTC epoch。 */
    private LocalDateTime localTime(Instant value) { return LocalDateTime.ofInstant(value, ZoneId.systemDefault()); }

    /** @return 不含密钥、凭据、配置原文或底层 cause 的错误 */
    private IllegalStateException failure() { return new IllegalStateException("AI 本地委托签发未就绪或身份无效"); }
}
