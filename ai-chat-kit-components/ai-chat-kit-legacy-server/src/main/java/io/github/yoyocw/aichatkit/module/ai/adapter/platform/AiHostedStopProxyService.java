package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceCallerRespDTO;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedStopProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedStopReceiver;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.enums.AiHostedStopOperation;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConsumedStopCommitService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopConsumerTokenProvider;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopOriginPrecheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** AI 自有停止代理：全新机器身份、独立消费者、来源预检、一次消费和本地事务提交。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.hosted-stop", name = "enabled", havingValue = "true")
public class AiHostedStopProxyService {
    /** 默认关闭、固定 audience 和完整接收方配置。 */
    private final AiHostedStopProperties properties;
    /** 每次停止独立创建并在 finally 撤销，不复用已结束 send 的机器会话。 */
    private final AiProxyMachineCredentialClient machineClient;
    /** 内部取得独立消费者身份，不取外部 Authorization 充当消费者。 */
    private final AiStopConsumerTokenProvider consumerProvider;
    /** AI 新协议及 Redis 命名空间的一次停止授权。 */
    private final AiHostedStopAuthorizationService authorization;
    /** 临时建立原用户作用域并恢复。 */
    private final PlatformStopScopeAdapter scope;
    /** 消费前来源及助手消息预检，不持锁调用认证。 */
    private final AiStopOriginPrecheckService precheck;
    /** 消费成功后再来源复查、锁定与 CAS，不修改旧停止实现。 */
    private final AiConsumedStopCommitService commit;
    /** 无来源历史消息不能通过候选停止入口。 */
    @Value("${ai-chat-kit.ai.delegated-entry.record-origin-enabled:false}")
    private boolean recordOriginEnabled;

    /**
     * 执行固定消息停止；票据不暴露给调用方，不承诺 Redis 消费和数据库更新跨库原子。
     * @param mode 控制器固定单聊或群聊
     * @param messageId 正数助手消息编号
     * @param userAuthorization 当前真实用户 Bearer 登录令牌
     * @throws IllegalStateException 配置、身份、票据、来源或提交无效，不重试或回放票据
     */
    @Transactional(propagation = Propagation.NEVER)
    public void stop(AiChatMode mode, Long messageId, String userAuthorization) {
        AiProxyMachineToken machine = null;
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive() || !recordOriginEnabled
                    || mode == null || messageId == null || messageId <= 0) { throw failure(); }
            AiHostedStopReceiver receiver = properties.requireReceiver(properties.getAudience());
            AiHostedStopOperation operation = mode == AiChatMode.SINGLE
                    ? AiHostedStopOperation.SINGLE_STOP : AiHostedStopOperation.GROUP_STOP;
            machine = machineClient.acquire();
            String ticket = authorization.issue("Bearer " + machine.getAccessToken(), userAuthorization,
                    operation, messageId, receiver.getAudience());
            consumeTicket(mode, messageId, ticket);
        } catch (RuntimeException ex) {
            throw failure();
        } finally {
            if (machine != null) { machineClient.revoke(machine.getAccessToken()); }
        }
    }

    /**
     * 保留委托停止入口的票据消费能力，仅接受 AI 自有协议，绝不请求旧 system 检查接口。
     * @param mode 固定停止模式
     * @param messageId 正数助手消息编号
     * @param ticket AI 自有一次性票据，原业务机器必须仍然有效
     * @throws IllegalStateException 票据、原身份、来源或消费无效，不回退旧协议
     */
    @Transactional(propagation = Propagation.NEVER)
    public void consumeTicket(AiChatMode mode, Long messageId, String ticket) {
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive() || !recordOriginEnabled
                    || mode == null || messageId == null || messageId <= 0) { throw failure(); }
            AiHostedStopReceiver receiver = properties.requireReceiver(properties.getAudience());
            AiHostedStopOperation operation = mode == AiChatMode.SINGLE
                    ? AiHostedStopOperation.SINGLE_STOP : AiHostedStopOperation.GROUP_STOP;
            String consumer = consumerProvider.getAuthorization();
            AiHostedStopResult inspected = authorization.inspect(consumer, ticket, operation, messageId, receiver.getAudience());
            AiCallerOrigin origin = origin(inspected);
            Set<String> apps = new HashSet<>(inspected.getOriginalCaller().getBinding().getAppIds());
            scope.execute(origin, context -> precheck.verify(context, origin, mode, messageId, apps));
            // 预检事务已结束，消费时不持有数据库锁；失败票据不放回。
            AiHostedStopResult consumed = authorization.consume(consumer, ticket, operation, messageId, receiver.getAudience());
            validateConsumed(inspected, consumed, apps);
            if (!recordOriginEnabled || !receiver.equals(properties.requireReceiver(receiver.getAudience()))
                    || !Objects.equals(receiver.getAudience(), properties.getAudience())
                    || System.currentTimeMillis() >= consumed.getSessionExpiresAt()) { throw failure(); }
            // 消费后的原始期限传到行锁后的提交边界，等待数据库锁不能延长票据授权。
            scope.execute(origin, context -> commit.commit(context, origin, mode, messageId, apps,
                    consumed.getSessionExpiresAt()));
        } catch (RuntimeException ex) {
            throw failure();
        }
    }

    /** 消费前后必须是同一原身份、会话和当前应用清单，不能把消费者身份传入本地提交。 */
    private void validateConsumed(AiHostedStopResult before, AiHostedStopResult after, Set<String> apps) {
        AiServiceCallerRespDTO left = before.getOriginalCaller();
        AiServiceCallerRespDTO right = after.getOriginalCaller();
        if (!Objects.equals(before.getUserSessionId(), after.getUserSessionId())
                || !Objects.equals(before.getOriginalMachineSessionId(), after.getOriginalMachineSessionId())
                || !Objects.equals(before.getUserId(), after.getUserId())
                || !Objects.equals(left.getTenantId(), right.getTenantId())
                || !Objects.equals(left.getClientId(), right.getClientId())
                || !Objects.equals(left.getClientRecordId(), right.getClientRecordId())
                || !Objects.equals(left.getBinding().getBusinessSystem(), right.getBinding().getBusinessSystem())
                || !Objects.equals(left.getBinding().getEnvironment(), right.getBinding().getEnvironment())
                || !apps.equals(new HashSet<>(right.getBinding().getAppIds()))) { throw failure(); }
    }

    /** 使用已认证原用户及业务实体构建来源，保留既有来源字段完整性验证。 */
    private AiCallerOrigin origin(AiHostedStopResult identity) {
        AiServiceCallerRespDTO caller = identity.getOriginalCaller();
        return new AiCallerOrigin(caller.getTenantId(), identity.getUserId(), caller.getClientRecordId(),
                caller.getClientId(), caller.getBinding().getBusinessSystem(), caller.getBinding().getEnvironment());
    }

    /** @return 不含票据、登录令牌或底层 cause 的错误 */
    private IllegalStateException failure() { return new IllegalStateException("当前无法完成 AI 授权停止"); }
}
