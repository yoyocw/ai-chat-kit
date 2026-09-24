package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.*;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service.AiHostedStopCoordinator;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.*;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConsumedStopCommitService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopConsumerTokenProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 仅林业server显式启用v2时的真实桥接，不由新模块依赖或组件扫描。 */
@AutoConfiguration(afterName = "io.github.yoyocw.aichatkit.module.ai.adapter.platform.host.PlatformInspectionAutoConfiguration",
        beforeName = "io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.autoconfigure.AiHostedProxyStopAutoConfiguration")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.hosted-proxy", name = "stop-enabled", havingValue = "true")
public class PlatformV2StopAutoConfiguration {
    /** @return 唯一真实机器认证桥，复用现有实体/应用绑定校验 */
    @Bean
    @ConditionalOnMissingBean(AiStopMachinePort.class)
    public AiStopMachinePort platformV2StopMachinePort(ListableBeanFactory factory) {
        return new PlatformV2StopMachineAdapter(unique(factory, AiSessionInspectionClient.class),
                unique(factory, AiInspectedDelegationService.class));
    }

    /** @return 真实普通用户凭据及固定停止权限核验 */
    @Bean
    @ConditionalOnMissingBean(AiStopUserProofPort.class)
    public AiStopUserProofPort platformV2StopUserProofPort(ListableBeanFactory factory) {
        return new PlatformV2StopUserProofAdapter(unique(factory, AiSessionInspectionClient.class));
    }

    /** @return 真实林业同步作用域，完整恢复旧上下文 */
    @Bean
    @ConditionalOnMissingBean(AiStopScopePort.class)
    public AiStopScopePort platformV2StopScopePort(ListableBeanFactory factory) {
        return new PlatformV2StopScopeAdapter(unique(factory, PlatformStopScopeAdapter.class));
    }

    /** @return 唯一真实Redis v2票据存储，不依赖旧v1开关 */
    @Bean
    @ConditionalOnMissingBean(AiStopTicketStorePort.class)
    public AiStopTicketStorePort platformV2StopTicketStorePort(ListableBeanFactory factory, Environment environment) {
        return new PlatformV2StopTicketStoreAdapter(unique(factory, StringRedisTemplate.class), namespace(environment));
    }

    /** @return 保留原锁顺序/期限/CAS/审计及afterCommit的实际数据库桥接 */
    @Bean
    @ConditionalOnMissingBean(AiConsumedStopPort.class)
    public AiConsumedStopPort platformV2ConsumedStopPort(ListableBeanFactory factory) {
        return new PlatformV2ConsumedStopAdapter(unique(factory, AiConsumedStopCommitService.class));
    }

    /** @return 宿主主动转接才调用的内部v2闭环，不自动替换旧HTTP */
    @Bean
    public PlatformV2HostedStopFacade platformV2HostedStopFacade(ListableBeanFactory factory, Environment environment) {
        namespace(environment);
        return new PlatformV2HostedStopFacade(unique(factory, AiProxyMachineCredentialClient.class),
                unique(factory, AiStopConsumerTokenProvider.class), unique(factory, AiHostedStopCoordinator.class));
    }

    /** 林业桥只承认自己的部署域，不为其它opaque宿主伪造林业数字上下文。 */
    private String namespace(Environment environment) {
        String namespace = environment.getRequiredProperty("ai-chat-kit.ai.starter.namespace");
        if (!"platform".equals(namespace)) { throw PlatformV2StopSupport.failure(); }
        return namespace;
    }

    /** 缺失或多个真实依赖均拒绝，不能靠Primary静默选错认证/数据域。 */
    private <T> T unique(ListableBeanFactory factory, Class<T> type) {
        String[] names = factory.getBeanNamesForType(type, true, false);
        if (names.length != 1) { throw new IllegalStateException("林业v2停止依赖缺失或不唯一：" + type.getSimpleName()); }
        return factory.getBean(names[0], type);
    }
}
