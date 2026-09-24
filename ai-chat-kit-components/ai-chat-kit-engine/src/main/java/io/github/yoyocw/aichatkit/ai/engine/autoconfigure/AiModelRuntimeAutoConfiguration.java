package io.github.yoyocw.aichatkit.ai.engine.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianGroupOutputParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地模型运行层可选装配，不扫描宿主Controller/Mapper或注册认证服务。
 * 默认关闭；既有AI宿主显式开启，独立使用者也可提供同类型实现。
 * 此配置不代表业务会话、持久化与身份宿主已完整抽离。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(BailianClient.class)
@ConditionalOnBean(AiRuntimeActivation.class)
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@EnableConfigurationProperties
public class AiModelRuntimeAutoConfiguration {
    /** @return AI 自有滚动记忆计算，独立宿主无需重新实现摘要算法。 */
    @Bean
    @ConditionalOnMissingBean
    public io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService aiConversationMemoryService(
            BailianProperties properties) {
        return new io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService(properties);
    }

    /** @return 保持原前缀的模型配置，宿主可显式替换，不包含自动生成的敏感toString。 */
    @Bean
    @ConditionalOnMissingBean
    public BailianProperties bailianProperties() { return new BailianProperties(); }

    /**
     * 创建本进程模型客户端，仅构造连接池，不在启动时调用外部服务。
     * @param properties 模型参数与网络边界
     * @return 本地同步/流式调用及本机取消实现，非远程AI代理
     */
    @Bean
    @ConditionalOnMissingBean
    public BailianClient bailianClient(BailianProperties properties) { return new BailianClient(properties); }

    /** @return 群聊输出解析组件，与业务数据库和宿主用户模型无关。 */
    @Bean
    @ConditionalOnMissingBean
    public BailianGroupOutputParser bailianGroupOutputParser() { return new BailianGroupOutputParser(); }
}
