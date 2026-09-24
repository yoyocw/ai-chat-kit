package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.dto.ServiceApiKeyAuthRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.*;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceCallerRespDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiUserDelegationRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.McpServiceJwtCodec;
import io.github.yoyocw.aichatkit.module.ai.config.AiInspectionCallerBinding;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** AI 承接用户委托验证：本地验签及业务绑定，认证侧仅复核真实机器、用户会话和入口权限。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.session-inspection", name = "enabled", havingValue = "true")
public class AiInspectedDelegationService {
    /** 受限、无缓存会话查询，不访问 system 数据表。 */
    private final AiSessionInspectionClient client;
    /** 复用已部署的受信任公钥、issuer、audience，迁移不生成或轮换密钥。 */
    private final McpServiceJwtCodec codec;
    /** 固定主体租户及 AI 自有客户端业务绑定。 */
    private final AiSessionInspectionProperties properties;

    /**
     * 为代理签发复核真实机器及 AI 自有业务绑定，不接受客户端编号替代机器证明。
     * @param authorization 本轮真实机器 Bearer 令牌
     * @return 当前客户端实体和批准的接入范围
     * @throws IllegalStateException 机器或业务绑定无效
     */
    public AiServiceCallerRespDTO validateCaller(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) { throw failure(); }
            OAuth2SessionInspectionReqDTO query = query("MACHINE");
            query.setAccessToken(authorization.substring(7));
            query.setExpectedUserId(0L);
            query.setRequiredScope("ai.invoke");
            query.setRequiredResource("platform-ai");
            OAuth2SessionInspectionRespDTO machine = client.inspect(query);
            AiServiceCallerRespDTO caller = new AiServiceCallerRespDTO();
            caller.setClientId(machine.getClientId());
            caller.setClientRecordId(machine.getClientRecordId());
            caller.setTenantId(machine.getTenantId());
            caller.setBinding(bindingFor(machine));
            caller.setExpiresTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(machine.getExpiresAtMillis()), ZoneId.systemDefault()));
            return caller;
        } catch (RuntimeException ex) { throw failure(); }
    }

    /**
     * @param authorization 真实机器 Bearer 令牌
     * @param delegation 用户委托 mcp_jwt_ 原文，不接收用户登录令牌
     * @param mode 代码固定 single 或 group
     * @return 同租户、同接入绑定的可信双重身份
     * @throws IllegalStateException 任一证明或实时权限无效；失败不回退旧认证
     */
    public AiUserDelegationRespDTO validate(String authorization, String delegation, String mode) {
        try {
            String permission = permissionFor(mode);
            if (authorization == null || !authorization.startsWith("Bearer ")
                    || delegation == null || delegation.length() > 16384 || !delegation.startsWith("mcp_jwt_")) {
                throw failure();
            }
            ServiceApiKeyAuthRespDTO proof = codec.verify(delegation);
            OAuth2SessionInspectionReqDTO machineQuery = query("MACHINE");
            machineQuery.setAccessToken(authorization.substring(7));
            machineQuery.setExpectedUserId(0L);
            machineQuery.setRequiredScope("ai.invoke");
            machineQuery.setRequiredResource("platform-ai");
            OAuth2SessionInspectionRespDTO machine = client.inspect(machineQuery);
            AiServiceBindingDTO binding = bindingFor(machine);
            // 先验证签名中的调用方绑定，再允许按用户会话编号执行受限复核。
            if (!Objects.equals(machine.getTenantId(), proof.getTenantId())
                    || !Objects.equals(machine.getClientId(), proof.getDelegationClientId())
                    || !Objects.equals(binding.getBusinessSystem(), proof.getDelegationBusinessSystem())
                    || !Objects.equals(binding.getEnvironment(), proof.getDelegationEnvironment())
                    || proof.getAccessTokenId() == null || proof.getAccessTokenId() <= 0
                    || proof.getServiceUserId() == null || proof.getServiceUserId() <= 0
                    || proof.getExpiresAtEpochSecond() == null) { throw failure(); }
            OAuth2SessionInspectionReqDTO userQuery = query("USER");
            userQuery.setSessionId(proof.getAccessTokenId());
            userQuery.setExpectedUserId(proof.getServiceUserId());
            userQuery.setRequiredPermissions(Collections.singletonList(permission));
            OAuth2SessionInspectionRespDTO user = client.inspect(userQuery);
            Instant expires = Instant.ofEpochSecond(proof.getExpiresAtEpochSecond());
            // 两次远程复核后重新检查期限，委托不能超出真实用户会话寿命。
            if (!expires.isAfter(Instant.now()) || expires.isAfter(Instant.ofEpochMilli(user.getExpiresAtMillis()))
                    || !Instant.ofEpochMilli(machine.getExpiresAtMillis()).isAfter(Instant.now())) { throw failure(); }
            return result(machine, user, binding, expires);
        } catch (RuntimeException ex) {
            throw failure();
        }
    }

    /** 复核条件只从可信部署配置和已验证证明构造，不接受任意权限参数。 */
    private OAuth2SessionInspectionReqDTO query(String type) {
        OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
        query.setSubjectType(type);
        query.setExpectedTenantId(properties.getSubjectTenantId());
        return query;
    }

    /** 实体编号、业务编号同时匹配；重复接入配置拒绝，清单拷贝后用于本次请求。 */
    AiServiceBindingDTO bindingFor(OAuth2SessionInspectionRespDTO machine) {
        AiServiceBindingDTO found = null;
        if (properties.getCallers() == null) { throw failure(); }
        for (AiInspectionCallerBinding caller : properties.getCallers()) {
            if (caller == null) { throw failure(); }
            if (!Objects.equals(caller.getClientRecordId(), machine.getClientRecordId())
                    || !Objects.equals(caller.getClientId(), machine.getClientId())) { continue; }
            AiServiceBindingDTO source = caller.getBinding();
            if (found != null || source == null || !Objects.equals(source.getTenantId(), machine.getTenantId())
                    || !matches(source.getBusinessSystem(), "[a-zA-Z0-9_-]{1,64}")
                    || !matches(source.getEnvironment(), "[a-zA-Z0-9_-]{1,64}")) { throw failure(); }
            found = new AiServiceBindingDTO();
            found.setBusinessSystem(source.getBusinessSystem());
            found.setEnvironment(source.getEnvironment());
            found.setTenantId(source.getTenantId());
            found.setAppIds(copyIds(source.getAppIds(), "[a-fA-F0-9]{32}", false));
            found.setUserAuthToolIds(copyIds(source.getUserAuthToolIds(), "tool_[a-zA-Z0-9-]{1,123}", true));
            found.setMcpIds(copyIds(source.getMcpIds(), "[a-zA-Z0-9_-]{1,128}", true));
        }
        if (found == null) { throw failure(); }
        return found;
    }

    /** 保留现有接入清单长度、唯一性和标识格式规则；缺失不等于空清单。 */
    private List<String> copyIds(List<String> values, String pattern, boolean emptyAllowed) {
        if (values == null) { throw failure(); }
        List<String> copy = new ArrayList<>(values);
        if (copy.size() > 10 || (!emptyAllowed && copy.isEmpty()) || new HashSet<>(copy).size() != copy.size()
                || !copy.stream().allMatch(value -> matches(value, pattern))) { throw failure(); }
        return Collections.unmodifiableList(copy);
    }

    /** 标识按管理员批准原值匹配，不自动归一化。 */
    private boolean matches(String value, String pattern) { return value != null && value.matches(pattern); }

    /** 固定入口权限，外部请求不能指定任意权限字符串。 */
    private String permissionFor(String mode) {
        if ("single".equals(mode)) { return "ai:chat:seed"; }
        if ("group".equals(mode)) { return "aigroup:chat:message:send"; }
        throw failure();
    }

    /** 仅在既有 LoginUser/DTO 边界把 UTC 时刻表示成本机 LocalDateTime，不用本地时间判定有效期。 */
    private AiUserDelegationRespDTO result(OAuth2SessionInspectionRespDTO machine,
            OAuth2SessionInspectionRespDTO user, AiServiceBindingDTO binding, Instant expires) {
        AiServiceCallerRespDTO caller = new AiServiceCallerRespDTO();
        caller.setClientId(machine.getClientId());
        caller.setClientRecordId(machine.getClientRecordId());
        caller.setTenantId(machine.getTenantId());
        caller.setExpiresTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(machine.getExpiresAtMillis()), ZoneId.systemDefault()));
        caller.setBinding(binding);
        OAuth2AccessTokenCheckRespDTO principal = new OAuth2AccessTokenCheckRespDTO();
        principal.setAccessTokenId(user.getSessionId());
        principal.setUserId(user.getUserId());
        principal.setUserType(user.getUserType());
        principal.setTenantId(user.getTenantId());
        principal.setExpiresTime(LocalDateTime.ofInstant(expires, ZoneId.systemDefault()));
        AiUserDelegationRespDTO result = new AiUserDelegationRespDTO();
        result.setCaller(caller);
        result.setUser(principal);
        return result;
    }

    /** @return 不附带凭据、底层响应或异常 cause 的认证错误 */
    private IllegalStateException failure() { return new IllegalStateException("AI 委托身份无效或接入绑定不匹配"); }
}
