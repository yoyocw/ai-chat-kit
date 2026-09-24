package io.github.yoyocw.aichatkit.ai.starter.autoconfigure;

import io.github.yoyocw.aichatkit.ai.starter.config.AiStarterProperties;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivation;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostAuthenticationBridge;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostSingleChatService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostGroupChatService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostConversationManagementService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostConversationShareService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiPublicConversationShareService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationManagementService;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** 注解与总开关同时启用才检查业务装配；不改变既有 engine 自动发现及未选择 Starter 的宿主。 */
@AutoConfiguration(afterName = {
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiModelRuntimeAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiApplicationConfigAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiSingleExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiGroupExecutionAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiConversationManagementAutoConfiguration",
        "io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiConversationShareAutoConfiguration"})
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnBean({AiRuntimeActivation.class, AiStarterMarker.class})
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.engine", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiStarterProperties.class)
public class AiStarterAutoConfiguration {
    /** 真实身份与目录用例齐备时开放独立授权门面，不产生默认目录。 */
    @Bean
    @ConditionalOnMissingBean(io.github.yoyocw.aichatkit.ai.starter.host.AiHostGroupAgentService.class)
    @ConditionalOnBean({AiInvocationContextPort.class, AiHostSessionPort.class, AiHostPermissionPort.class,
            io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupAgentCatalogService.class})
    public io.github.yoyocw.aichatkit.ai.starter.host.AiHostGroupAgentService aiHostGroupAgentService(AiHostAuthenticationBridge authentication,
            io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupAgentCatalogService catalog) {
        return new io.github.yoyocw.aichatkit.ai.starter.host.AiHostGroupAgentService(authentication, catalog);
    }
    /**
     * 装配登录用户的分享管理能力，公开读取不使用此身份入口。
     * @param authenticationBridge 宿主真实认证边界
     * @param shareService 分享事务及归属核验服务
     * @return 分别检查单群聊创建、撤销权限的门面
     */
    @Bean
    @ConditionalOnMissingBean(AiHostConversationShareService.class)
    @ConditionalOnBean({AiInvocationContextPort.class, AiHostSessionPort.class,
            AiHostPermissionPort.class, AiConversationShareService.class})
    public AiHostConversationShareService aiHostConversationShareService(
            AiHostAuthenticationBridge authenticationBridge, AiConversationShareService shareService) {
        return new AiHostConversationShareService(authenticationBridge, shareService);
    }

    /**
     * 装配独立的分享码读取能力，不把匿名访问者映射为创建者，不新增公开 HTTP。
     * @param shareService 固定部署范围、实时检查有效状态的分享引擎
     * @return 仅接受聊天模式和分享码的公开读取门面
     */
    @Bean
    @ConditionalOnMissingBean(AiPublicConversationShareService.class)
    @ConditionalOnBean(AiConversationShareService.class)
    public AiPublicConversationShareService aiPublicConversationShareService(AiConversationShareService shareService) {
        return new AiPublicConversationShareService(shareService);
    }

    /**
     * 装配会话管理授权入口，存储和事务服务缺失时由启动诊断拒绝。
     * @param authenticationBridge 宿主真实认证边界
     * @param managementService 完成最终身份复核的管理事务服务
     * @return 会话列表、历史及修改操作入口，不提供默认 HTTP
     */
    @Bean
    @ConditionalOnMissingBean(AiHostConversationManagementService.class)
    @ConditionalOnBean({AiInvocationContextPort.class, AiHostSessionPort.class,
            AiHostPermissionPort.class, AiConversationManagementService.class})
    public AiHostConversationManagementService aiHostConversationManagementService(
            AiHostAuthenticationBridge authenticationBridge, AiConversationManagementService managementService) {
        return new AiHostConversationManagementService(authenticationBridge, managementService);
    }

    /**
     * 仅在完整群聊同步引擎和真实身份适配齐备时装配，流组件本身不足以满足此条件。
     * @param authenticationBridge 宿主认证边界
     * @param executionService 群聊同步事务服务
     * @return 独立检查群聊权限的创建、发送及停止门面
     */
    @Bean
    @ConditionalOnMissingBean(AiHostGroupChatService.class)
    @ConditionalOnBean({AiInvocationContextPort.class, AiHostSessionPort.class,
            AiHostPermissionPort.class, AiGroupChatExecutionService.class})
    public AiHostGroupChatService aiHostGroupChatService(AiHostAuthenticationBridge authenticationBridge,
                                                        AiGroupChatExecutionService executionService) {
        return new AiHostGroupChatService(authenticationBridge, executionService);
    }

    /**
     * 装配可调用的单聊授权入口，不增加默认 HTTP 路由。
     * @param authenticationBridge 真实宿主认证边界
     * @param executionService 已装配的单聊事务服务
     * @return 宿主 Controller 可注入的发送、停止门面
     */
    @Bean
    @ConditionalOnMissingBean(AiHostSingleChatService.class)
    @ConditionalOnBean({AiInvocationContextPort.class, AiHostSessionPort.class,
            AiHostPermissionPort.class, AiChatExecutionService.class})
    public AiHostSingleChatService aiHostSingleChatService(AiHostAuthenticationBridge authenticationBridge,
                                                          AiChatExecutionService executionService) {
        return new AiHostSingleChatService(authenticationBridge, executionService);
    }

    /**
     * 仅在真实宿主适配器齐备时装配认证边界，缺失由最终启动检查报告。
     * @param properties 宿主固定部署命名空间
     * @param contextPort 可信身份捕获
     * @param sessionPort 真实会话复核
     * @param permissionPort 真实权限判定
     * @return 用户认证桥接；其存在不代表已完成 JWT 验签或业务入口接线
     */
    @Bean
    @ConditionalOnMissingBean(AiHostAuthenticationBridge.class)
    @ConditionalOnBean({AiInvocationContextPort.class, AiHostSessionPort.class, AiHostPermissionPort.class})
    public AiHostAuthenticationBridge aiHostAuthenticationBridge(AiStarterProperties properties,
            AiInvocationContextPort contextPort, AiHostSessionPort sessionPort, AiHostPermissionPort permissionPort) {
        return new AiHostAuthenticationBridge(properties.getNamespace(), contextPort, sessionPort, permissionPort);
    }

    /**
     * 检查最终容器，而不是在条件解析阶段把缺依赖静默视为不需要该业务能力。
     * @param beanFactory 宿主容器，仅用于查找固定的适配类型
     * @param properties 宿主声明的业务模式，不含秘密
     * @return 启动完成前执行的装配完整性检查器，不调用模型或业务数据库
     */
    @Bean
    public AiStarterDependencyVerifier aiStarterDependencyVerifier(ListableBeanFactory beanFactory,
                                                                  AiStarterProperties properties) {
        return new AiStarterDependencyVerifier(beanFactory, properties);
    }
}
