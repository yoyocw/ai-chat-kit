package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import java.util.Objects;
import java.util.Set;

/** 仅预检内部使用的真实林业来源桥，不注册第二个公共来源端口，不改变旧来源记录。 */
public final class PlatformStopOriginAdapter implements AiMessageOriginPort {
    /** 原MyBatis来源实现，保留其真实租户/助手/应用验证。 */
    private final AiMessageOriginPort delegate;
    /** @param delegate 与预检事务数据源匹配的原来源实现 */
    public PlatformStopOriginAdapter(AiMessageOriginPort delegate) { this.delegate = Objects.requireNonNull(delegate); }

    /** 保留记录契约，预检服务本身不会调用写方法。 */
    @Override
    public void record(AiInvocationContext context, AiChatMode mode, Long messageId, String appId) {
        delegate.record(context, mode, messageId, appId);
    }

    /** 通用opaque来源在真实林业作用域内才可同值映射，原实现继续验证实际租户及目标助手。 */
    @Override
    public String verify(AiInvocationContext context, AiCallerOrigin caller, AiChatMode mode,
                         Long messageId, Set<String> allowedAppIds) {
        if (context == null || caller == null || !"platform".equals(context.getNamespace())
                || !Objects.equals(context.getTenantId(), caller.getTenantIdentifier())
                || !Objects.equals(context.getActorId(), caller.getActorIdentifier())) { throw PlatformV2StopSupport.failure(); }
        AiCallerOrigin numeric = new AiCallerOrigin(PlatformV2StopSupport.number(caller.getTenantIdentifier(), true),
                PlatformV2StopSupport.number(caller.getActorIdentifier(), false),
                PlatformV2StopSupport.number(caller.getClientRecordIdentifier(), false), caller.getClientId(),
                caller.getBusinessSystem(), caller.getEnvironment());
        return delegate.verify(context, numeric, mode, messageId, allowedAppIds);
    }
}
