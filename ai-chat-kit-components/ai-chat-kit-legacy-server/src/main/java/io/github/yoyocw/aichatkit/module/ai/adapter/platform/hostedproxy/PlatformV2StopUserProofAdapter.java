package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopReceiver;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopUserProofPort;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.enums.AiHostedStopOperation;
import java.util.Collections;
import java.util.Objects;

/** 复用真实用户inspect及旧宿主固定停止权限；无JWT签发/验签回退或发送权限替代。 */
public final class PlatformV2StopUserProofAdapter implements AiStopUserProofPort {
    /** 检查真实租户、后台用户及当前权限的宿主客户端。 */
    private final AiSessionInspectionClient inspector;
    /** @param inspector 真实宿主检查能力 */
    public PlatformV2StopUserProofAdapter(AiSessionInspectionClient inspector) {
        this.inspector = Objects.requireNonNull(inspector);
    }

    /** 只接受普通真实用户凭据；MCP和停止票据不能当作用户证明。 */
    @Override
    public AiHostSession verify(String userProof, AiStopReceiver receiver, AiChatMode mode) {
        try {
            OAuth2SessionInspectionReqDTO request = query(receiver, mode);
            request.setAccessToken(PlatformV2StopSupport.raw(userProof));
            return result(inspector.inspect(request), receiver);
        } catch (RuntimeException exception) { throw PlatformV2StopSupport.failure(); }
    }

    /** 复核原user/session；权限服务从实际用户当前角色判断。 */
    @Override
    public AiHostSession recheck(AiHostSession expected, AiStopReceiver receiver, AiChatMode mode) {
        try {
            OAuth2SessionInspectionReqDTO request = query(receiver, mode);
            request.setExpectedUserId(PlatformV2StopSupport.number(expected.getActorId(), false));
            request.setSessionId(PlatformV2StopSupport.number(expected.getSessionId(), false));
            return result(inspector.inspect(request), receiver);
        } catch (RuntimeException exception) { throw PlatformV2StopSupport.failure(); }
    }

    /** 固定两种模式对应的旧宿主停止权限，不接受外部权限字符串。 */
    private OAuth2SessionInspectionReqDTO query(AiStopReceiver receiver, AiChatMode mode) {
        if (receiver == null || !"platform".equals(receiver.getNamespace())
                || (mode != AiChatMode.SINGLE && mode != AiChatMode.GROUP)) { throw PlatformV2StopSupport.failure(); }
        OAuth2SessionInspectionReqDTO request = new OAuth2SessionInspectionReqDTO();
        request.setSubjectType("USER");
        request.setExpectedTenantId(PlatformV2StopSupport.number(receiver.getTenantId(), true));
        request.setRequiredPermissions(Collections.singletonList((mode == AiChatMode.SINGLE
                ? AiHostedStopOperation.SINGLE_STOP : AiHostedStopOperation.GROUP_STOP).getPermission()));
        return request;
    }

    /** 时间直接保留真实UTC毫秒，不转换本地时间或重算生命周期。 */
    private AiHostSession result(OAuth2SessionInspectionRespDTO user, AiStopReceiver receiver) {
        return new AiHostSession(receiver.getNamespace(), user.getTenantId().toString(), user.getUserId().toString(),
                user.getSessionId().toString(), user.getExpiresAtMillis());
    }
}
