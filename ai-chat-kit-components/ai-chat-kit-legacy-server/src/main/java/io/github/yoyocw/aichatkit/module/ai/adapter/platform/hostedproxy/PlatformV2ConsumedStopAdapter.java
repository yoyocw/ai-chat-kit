package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiConsumedStopCommand;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiConsumedStopPort;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopTicket;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConsumedStopCommitService;
import java.util.Objects;

/** 复用实际MyBatis锁/来源/CAS/审计事务，不复制停止业务，不是HTTP认证入口。 */
public final class PlatformV2ConsumedStopAdapter implements AiConsumedStopPort {
    /** 已修复锁等待期限缝隙的真实本地提交服务代理。 */
    private final AiConsumedStopCommitService delegate;
    /** @param delegate 现有Spring事务代理，不能手工new替代 */
    public PlatformV2ConsumedStopAdapter(AiConsumedStopCommitService delegate) { this.delegate = Objects.requireNonNull(delegate); }

    /** 严格保留原身份和截止时间，旧服务在锁后、CAS前及返回前再次校验。 */
    @Override
    public void commit(AiConsumedStopCommand command) {
        if (command == null || command.getTicket() == null) { throw PlatformV2StopSupport.failure(); }
        AiStopTicket ticket = command.getTicket();
        long expiry = command.getAuthorizationExpiresAtMillis();
        if (ticket.getVersion() != 2 || expiry <= System.currentTimeMillis() || expiry > ticket.getExpiresAtMillis()
                || expiry > ticket.getUser().getExpiresAtMillis()
                || expiry > ticket.getOriginalMachine().getExpiresAtMillis()) { throw PlatformV2StopSupport.failure(); }
        PlatformV2StopSupport.context(command.getContext(), ticket.getUser());
        AiCallerOrigin original = command.getOriginalCaller();
        if (!original.getClientRecordIdentifier().equals(ticket.getOriginalMachine().getClientRecordId())
                || !original.getClientId().equals(ticket.getOriginalMachine().getClientId())
                || !original.getBusinessSystem().equals(ticket.getReceiver().getBusinessSystem())
                || !original.getEnvironment().equals(ticket.getReceiver().getEnvironment())) {
            throw PlatformV2StopSupport.failure();
        }
        AiCallerOrigin numeric = PlatformV2StopSupport.numericOrigin(ticket.getUser(), original);
        delegate.commit(command.getContext(), numeric, ticket.getMode(), ticket.getMessageId(),
                ticket.getOriginalMachine().getAllowedAppIds(), expiry);
    }
}
