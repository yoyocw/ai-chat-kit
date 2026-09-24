package io.github.yoyocw.aichatkit.module.ai.config;

import io.github.yoyocw.aichatkit.compat.framework.common.util.object.BeanUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 可选停止授权配置，仅开启调用时校验，无配置文件或数据库回退。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-chat-kit.ai.hosted-stop")
public class AiHostedStopProperties {
    /** 默认关闭；关闭时签发、检查和消费全部拒绝。 */
    private boolean enabled;
    /** 此部署停止代理唯一接收方，必须命中 receivers，不接受用户传入。 */
    private String audience;
    /** 部署接收方列表，开启使用时要求1至32项且 audience 唯一。 */
    private List<AiHostedStopReceiver> receivers = new ArrayList<AiHostedStopReceiver>();

    /**
     * 返回独立配置快照，避免将共享可变绑定放入票据。
     * @param audience 固定目标标识，必须命中完整有效部署列表
     * @return 当前接收方快照
     * @throws IllegalStateException 未启用、配置无效或目标未配置
     */
    public AiHostedStopReceiver requireReceiver(String audience) {
        if (!enabled || receivers == null || receivers.isEmpty() || receivers.size() > 32) {
            throw new IllegalStateException("停止授权未启用或接收方配置无效");
        }
        Set<String> audiences = new HashSet<String>();
        AiHostedStopReceiver result = null;
        for (AiHostedStopReceiver item : receivers) {
            if (!valid(item) || !audiences.add(item.getAudience())) {
                throw new IllegalStateException("停止授权接收方配置无效");
            }
            if (item.getAudience().equals(audience)) {
                result = BeanUtils.toBean(item, AiHostedStopReceiver.class);
            }
        }
        if (result == null) { throw new IllegalStateException("停止授权接收方未配置"); }
        return result;
    }

    /** 完整部署绑定必须区分原调用方和消费者的实体及字符串标识。 */
    private boolean valid(AiHostedStopReceiver item) {
        return item != null && identifier(item.getAudience()) && identifier(item.getBusinessSystem())
                && identifier(item.getEnvironment()) && identifier(item.getResourceId())
                && identifier(item.getOriginalClientId()) && identifier(item.getConsumerClientId())
                && item.getTenantId() != null && item.getTenantId() >= 0
                && item.getOriginalClientRecordId() != null && item.getOriginalClientRecordId() > 0
                && item.getConsumerClientRecordId() != null && item.getConsumerClientRecordId() > 0
                && !item.getOriginalClientRecordId().equals(item.getConsumerClientRecordId())
                && !item.getOriginalClientId().equals(item.getConsumerClientId());
    }

    /** 部署标识不自动修正空白或大小写。 */
    private boolean identifier(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,64}");
    }
}
