package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopReceiver;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.config.AiHostedProxyStopProperties;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.config.AiStopReceiverProperties;
import java.util.HashSet;
import java.util.Set;

/** 接收方配置一次性严格快照，避免请求覆盖身份、scope、resource及namespace。 */
final class AiStopReceiverPolicy {
    private AiStopReceiverPolicy() { }
    /** @return 部署固定目标，整个列表有重复/无效项也拒绝，不能只跳过错误项 */
    static AiStopReceiver select(String namespace, AiHostedProxyStopProperties properties) {
        AiStopChecks.text(namespace);
        if (!properties.isStopEnabled() || properties.getStopReceivers() == null
                || properties.getStopReceivers().isEmpty() || properties.getStopReceivers().size() > 32) {
            throw AiStopChecks.denied();
        }
        Set<String> audiences = new HashSet<>();
        AiStopReceiver selected = null;
        for (AiStopReceiverProperties source : properties.getStopReceivers()) {
            if (source == null) { throw AiStopChecks.denied(); }
            AiStopReceiver receiver = source.snapshot(namespace);
            validate(receiver);
            if (!audiences.add(receiver.getAudience())) { throw AiStopChecks.denied(); }
            if (receiver.getAudience().equals(properties.getStopAudience())) { selected = receiver; }
        }
        if (selected == null) { throw AiStopChecks.denied(); }
        return selected;
    }

    /** 完整绑定两个不同机器实体，缺配置不能扩为任意客户端。 */
    static void validate(AiStopReceiver receiver) {
        if (receiver == null) { throw AiStopChecks.denied(); }
        AiStopChecks.text(receiver.getNamespace());
        AiStopChecks.text(receiver.getAudience());
        AiStopChecks.text(receiver.getTenantId());
        AiStopChecks.text(receiver.getBusinessSystem());
        AiStopChecks.text(receiver.getEnvironment());
        AiStopChecks.text(receiver.getOriginalClientId());
        AiStopChecks.text(receiver.getOriginalClientRecordId());
        AiStopChecks.text(receiver.getConsumerClientId());
        AiStopChecks.text(receiver.getConsumerClientRecordId());
        AiStopChecks.text(receiver.getOriginalResourceId());
        AiStopChecks.text(receiver.getConsumerResourceId());
        AiStopChecks.text(receiver.getOriginalScope());
        AiStopChecks.text(receiver.getConsumerScope());
        if (receiver.getOriginalClientId().equals(receiver.getConsumerClientId())
                || receiver.getOriginalClientRecordId().equals(receiver.getConsumerClientRecordId())
                || receiver.getOriginalScope().equals(receiver.getConsumerScope())) { throw AiStopChecks.denied(); }
    }
}
