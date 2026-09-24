package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/** 独立可选停止配置；与旧hosted-stop的v1开关及票据隔离，缺省关闭。 */
@ConfigurationProperties(prefix = "ai-chat-kit.ai.hosted-proxy")
public class AiHostedProxyStopProperties {
    /** 是否启用v2停止协调，默认false。 */
    private boolean stopEnabled;
    /** 唯一由宿主选定的停止接收方，不从HTTP读取。 */
    private String stopAudience;
    /** 1..32条完整接收方策略，audience不可重复。 */
    private List<AiStopReceiverProperties> stopReceivers = new ArrayList<>();
    /** @return v2装配开关 */
    public boolean isStopEnabled() { return stopEnabled; }
    /** @param value 显式v2装配开关 */
    public void setStopEnabled(boolean value) { stopEnabled = value; }
    /** @return 部署固定接收方 */
    public String getStopAudience() { return stopAudience; }
    /** @param value 部署固定接收方 */
    public void setStopAudience(String value) { stopAudience = value; }
    /** @return 部署接收方策略 */
    public List<AiStopReceiverProperties> getStopReceivers() { return stopReceivers; }
    /** @param value 完整接收方策略，不能来自客户端 */
    public void setStopReceivers(List<AiStopReceiverProperties> value) { stopReceivers = value; }
}
