package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.autoconfigure;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.*;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.config.AiHostedProxyStopProperties;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service.AiHostedStopCoordinator;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopOriginPrecheckService;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** 可选停止显式装配，缺一个真实SPI就拒绝，不扫描或提供假身份/内存票据默认实现。 */
@AutoConfiguration
@ConditionalOnBean(type = "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation")
@ConditionalOnProperty(prefix = "ai-chat-kit.ai", name = {"engine.enabled", "hosted-proxy.stop-enabled"}, havingValue = "true")
@EnableConfigurationProperties(AiHostedProxyStopProperties.class)
public class AiHostedProxyStopAutoConfiguration {
    /** @return 不依赖林业或Redis的通用协调器；依赖实现必须唯一 */
    @Bean
    public AiHostedStopCoordinator aiHostedStopCoordinator(AiHostedProxyStopProperties properties,
            Environment environment, ListableBeanFactory factory) {
        return new AiHostedStopCoordinator(environment.getRequiredProperty("ai-chat-kit.ai.starter.namespace"), properties,
                unique(factory, AiStopMachinePort.class), unique(factory, AiStopUserProofPort.class),
                unique(factory, AiStopScopePort.class), unique(factory, AiStopTicketStorePort.class),
                unique(factory, AiConsumedStopPort.class), unique(factory, AiStopOriginPrecheckService.class));
    }

    /** 多候选即拒绝，不依赖Primary选择错误的认证或存储域。 */
    private <T> T unique(ListableBeanFactory factory, Class<T> type) {
        String[] names = factory.getBeanNamesForType(type, true, false);
        if (names.length != 1) { throw new IllegalStateException("v2停止依赖缺失或不唯一：" + type.getSimpleName()); }
        return factory.getBean(names[0], type);
    }
}
