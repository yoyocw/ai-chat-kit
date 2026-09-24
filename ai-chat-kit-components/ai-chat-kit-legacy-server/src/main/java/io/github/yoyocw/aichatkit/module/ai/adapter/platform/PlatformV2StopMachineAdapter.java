package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.*;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy.PlatformV2StopSupport;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

/** 显式注册的v2真实机器桥；与原bindingFor同包复用其完整绑定规则，不扩大其可见性。 */
public final class PlatformV2StopMachineAdapter implements AiStopMachinePort {
    /** 实际受限会话检查客户端。 */
    private final AiSessionInspectionClient inspector;
    /** 当前客户端实体与系统/环境/应用白名单绑定。 */
    private final AiInspectedDelegationService bindings;

    /** @param inspector 真实OAuth检查 @param bindings 已有真实接入绑定能力 */
    public PlatformV2StopMachineAdapter(AiSessionInspectionClient inspector, AiInspectedDelegationService bindings) {
        this.inspector = Objects.requireNonNull(inspector); this.bindings = Objects.requireNonNull(bindings);
    }

    /** 初次验证真实机器普通凭据，不能把用户或MCP凭据当机器身份。 */
    @Override
    public AiStopMachineIdentity authenticate(String bearer, AiStopReceiver receiver, AiStopMachineRole role) {
        try {
            OAuth2SessionInspectionReqDTO request = query(receiver, role);
            request.setAccessToken(PlatformV2StopSupport.raw(bearer));
            return result(inspector.inspect(request), receiver, role);
        } catch (RuntimeException exception) { throw PlatformV2StopSupport.failure(); }
    }

    /** 只查询已验证原session，实体、scope及resource继续与固定receiver匹配。 */
    @Override
    public AiStopMachineIdentity recheck(AiStopMachineIdentity expected, AiStopReceiver receiver, AiStopMachineRole role) {
        try {
            OAuth2SessionInspectionReqDTO request = query(receiver, role);
            request.setSessionId(PlatformV2StopSupport.number(expected.getSessionId(), false));
            return result(inspector.inspect(request), receiver, role);
        } catch (RuntimeException exception) { throw PlatformV2StopSupport.failure(); }
    }

    /** 规范数值转换只发生在林业宿主，不为通用opaque身份捏造数字。 */
    private OAuth2SessionInspectionReqDTO query(AiStopReceiver receiver, AiStopMachineRole role) {
        if (receiver == null || role == null || !"platform".equals(receiver.getNamespace())) {
            throw PlatformV2StopSupport.failure();
        }
        boolean original = role == AiStopMachineRole.ORIGINAL;
        OAuth2SessionInspectionReqDTO request = new OAuth2SessionInspectionReqDTO();
        request.setSubjectType("MACHINE");
        request.setExpectedUserId(0L);
        request.setExpectedTenantId(PlatformV2StopSupport.number(receiver.getTenantId(), true));
        request.setExpectedClientRecordId(PlatformV2StopSupport.number(original
                ? receiver.getOriginalClientRecordId() : receiver.getConsumerClientRecordId(), false));
        request.setRequiredScope(original ? receiver.getOriginalScope() : receiver.getConsumerScope());
        request.setRequiredResource(original ? receiver.getOriginalResourceId() : receiver.getConsumerResourceId());
        return request;
    }

    /** 当前原机器应用白名单只来自bindingFor，消费者不获得来源应用列表。 */
    private AiStopMachineIdentity result(OAuth2SessionInspectionRespDTO machine, AiStopReceiver receiver,
                                        AiStopMachineRole role) {
        boolean original = role == AiStopMachineRole.ORIGINAL;
        if (!Objects.equals(machine.getClientId(), original ? receiver.getOriginalClientId() : receiver.getConsumerClientId())) {
            throw PlatformV2StopSupport.failure();
        }
        AiServiceBindingDTO binding = original ? bindings.bindingFor(machine) : null;
        return new AiStopMachineIdentity(receiver.getNamespace(), machine.getTenantId().toString(),
                machine.getSessionId().toString(), machine.getClientRecordId().toString(), machine.getClientId(),
                binding == null ? null : binding.getBusinessSystem(), binding == null ? null : binding.getEnvironment(),
                binding == null ? Collections.emptySet() : new HashSet<>(binding.getAppIds()), machine.getExpiresAtMillis());
    }
}
