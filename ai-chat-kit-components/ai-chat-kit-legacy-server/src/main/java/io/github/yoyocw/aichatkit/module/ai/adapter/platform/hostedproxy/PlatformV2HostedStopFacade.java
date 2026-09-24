package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service.AiHostedStopCoordinator;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiProxyMachineCredentialClient;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiProxyMachineToken;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopConsumerTokenProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;

/** 显式启用的真实宿主内部v2闭环；不注册HTTP，不替换旧v1路由。 */
public final class PlatformV2HostedStopFacade {
    /** 每次取得新的真实原机器，结束后finally撤销。 */
    private final AiProxyMachineCredentialClient machines;
    /** 仅从宿主获取独立消费者凭据，不能用请求Authorization冒充。 */
    private final AiStopConsumerTokenProvider consumers;
    /** 中立v2票据及来源/提交编排。 */
    private final AiHostedStopCoordinator coordinator;

    /** @param machines 已有真实OAuth机器客户端 @param consumers 真实独立消费者 @param coordinator v2编排 */
    public PlatformV2HostedStopFacade(AiProxyMachineCredentialClient machines, AiStopConsumerTokenProvider consumers,
                                     AiHostedStopCoordinator coordinator) {
        this.machines = Objects.requireNonNull(machines); this.consumers = Objects.requireNonNull(consumers);
        this.coordinator = Objects.requireNonNull(coordinator);
    }

    /**
     * @param mode 宿主入口固定停止模式 @param messageId 原助手目标 @param userAuthorization 当前真实普通用户凭据
     * 原机器及消费者由宿主取得；来源不符、过期或失败时不回放票据，原机器仍尽力撤销。
     */
    public void stop(AiChatMode mode, Long messageId, String userAuthorization) {
        AiProxyMachineToken machine = null;
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || (mode != AiChatMode.SINGLE && mode != AiChatMode.GROUP)
                    || messageId == null || messageId <= 0) { throw PlatformV2StopSupport.failure(); }
            PlatformV2StopSupport.raw(userAuthorization);
            machine = machines.acquire();
            String ticket = coordinator.issue("Bearer " + machine.getAccessToken(), userAuthorization, mode, messageId);
            coordinator.consume(consumers.getAuthorization(), ticket, mode, messageId);
        } catch (RuntimeException exception) { throw PlatformV2StopSupport.failure(); }
        finally {
            if (machine != null) { machines.revoke(machine.getAccessToken()); }
        }
    }
}
