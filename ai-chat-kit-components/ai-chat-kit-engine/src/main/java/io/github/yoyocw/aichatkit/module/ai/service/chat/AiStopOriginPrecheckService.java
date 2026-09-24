package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import org.springframework.transaction.PlatformTransactionManager;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import java.util.Objects;
import java.util.Set;

/** 来源预检独立短事务；显式选定宿主事务管理器，不依赖组件扫描，结束后才能消费票据。 */
public class AiStopOriginPrecheckService {
    /** 必须在所选宿主事务中检查来源的存储端口。 */
    private final AiMessageOriginPort originPort;
    /** 显式事务模板，固定REQUIRES_NEW/只读；不让认证请求持有数据库锁。 */
    private final AiTransactionExecutor transaction;

    /** @param originPort 同一宿主数据源的来源端口 @param transactionManager 唯一且与来源端口匹配的真实事务管理器 */
    public AiStopOriginPrecheckService(AiMessageOriginPort originPort, PlatformTransactionManager transactionManager) {
        this(originPort, new AiTransactionExecutor(transactionManager, AiTransactionMode.REUSE_HOST));
    }

    /** @param originPort 同源来源端口 @param transaction 所选AI事务，不依赖全局管理器候选 */
    public AiStopOriginPrecheckService(AiMessageOriginPort originPort, AiTransactionExecutor transaction) {
        this.originPort = Objects.requireNonNull(originPort);
        this.transaction = Objects.requireNonNull(transaction);
    }

    /**
     * 验证原消息来源；成功只准进入消费阶段，不授予停止能力。
     * @param context 已认证宿主身份 @param caller 已核验原调用方 @param mode 固定入口模式
     * @param messageId 固定助手目标 @param allowedAppIds 当前源核验的应用集合，不接受请求自报
     */
    public void verify(AiInvocationContext context, AiCallerOrigin caller, AiChatMode mode,
                       Long messageId, Set<String> allowedAppIds) {
        transaction.requiresNewReadOnly(() -> {
            originPort.verify(context, caller, mode, messageId, allowedAppIds);
            return null;
        }, -1);
    }
}
