package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopScopePort;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.PlatformStopScopeAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import java.util.Objects;
import java.util.function.Consumer;

/** 仅显式启用后桥接旧真实授权作用域；委托原实现同步执行并finally恢复身份/租户。 */
public final class PlatformV2StopScopeAdapter implements AiStopScopePort {
    /** 已有的同步上下文建立与恢复实现。 */
    private final PlatformStopScopeAdapter delegate;
    /** @param delegate 原宿主授权作用域，不提供默认身份 */
    public PlatformV2StopScopeAdapter(PlatformStopScopeAdapter delegate) { this.delegate = Objects.requireNonNull(delegate); }

    /** 将真实源已核验的身份映射回旧宿主数值域，回调前再次比对实际捕获身份。 */
    @Override
    public void execute(AiHostSession verifiedUser, AiCallerOrigin originalCaller, Consumer<AiInvocationContext> localStage) {
        AiCallerOrigin numeric = PlatformV2StopSupport.numericOrigin(verifiedUser, originalCaller);
        delegate.execute(numeric, context -> {
            PlatformV2StopSupport.context(context, verifiedUser);
            localStage.accept(context);
        });
    }
}
