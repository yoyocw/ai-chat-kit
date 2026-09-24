package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import java.util.function.Consumer;

/** 已经过协调层真实授权的原用户同步作用域；与不授予登录身份的普通执行隔离端口不同。 */
public interface AiStopScopePort {
    /**
     * @param verifiedUser 真实源已核验的原用户会话，不接受HTTP反序列化身份
     * @param originalCaller 已核验原业务来源
     * @param localStage 仅本地短事务预检或提交，不放远程认证
     * 建立后必须重新捕获核对namespace/tenant/actor，异常和成功均恢复原上下文。
     */
    void execute(AiHostSession verifiedUser, AiCallerOrigin originalCaller, Consumer<AiInvocationContext> localStage);
}
