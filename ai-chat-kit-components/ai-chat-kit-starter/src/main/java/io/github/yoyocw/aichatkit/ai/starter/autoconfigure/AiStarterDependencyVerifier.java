package io.github.yoyocw.aichatkit.ai.starter.autoconfigure;

import io.github.yoyocw.aichatkit.ai.starter.config.AiStarterProperties;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostAuthenticationBridge;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostSingleChatService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostGroupChatService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostConversationManagementService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostConversationShareService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiPublicConversationShareService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiConversationSharePort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationManagementService;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationStorePort;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutor;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiHostPermissionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSessionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatBusinessPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatStreamStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiSingleChatExecutor;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamExecutor;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 检查所选业务模式的真实装配；通过只表示结构齐备，不等于远端调用、权限或事务行为已验证。 */
public final class AiStarterDependencyVerifier implements SmartInitializingSingleton {
    /** 只解析代码定义的依赖类型，不接受请求指定 Bean 名称。 */
    private final ListableBeanFactory beanFactory;
    /** 宿主声明模式的部署配置。 */
    private final AiStarterProperties properties;

    /** @param beanFactory 宿主容器 @param properties 声明模式配置 */
    public AiStarterDependencyVerifier(ListableBeanFactory beanFactory, AiStarterProperties properties) {
        this.beanFactory = beanFactory;
        this.properties = properties;
    }

    /** 所有普通单例初始化后复核，缺失/歧义时阻止应用启动；不以模型客户端存在代替聊天可用。 */
    @Override
    public void afterSingletonsInstantiated() {
        if (properties.getModes() == null || properties.getModes().isEmpty() || properties.getModes().contains(null)) {
            throw new IllegalStateException("AI Starter 必须声明至少一种有效聊天模式");
        }
        Set<AiChatMode> modes = EnumSet.copyOf(properties.getModes());
        List<String> missing = new ArrayList<>();
        if (properties.getNamespace() == null || properties.getNamespace().trim().isEmpty()) {
            missing.add("ai-chat-kit.ai.starter.namespace（宿主固定命名空间）");
        }
        require(missing, AiInvocationContextPort.class, AiHostSessionPort.class, AiHostPermissionPort.class,
                AiHostAuthenticationBridge.class);
        require(missing, AiConversationStorePort.class, AiConversationManagementService.class,
                AiHostConversationManagementService.class);
        require(missing, AiConversationSharePort.class, AiConversationShareService.class,
                AiHostConversationShareService.class, AiPublicConversationShareService.class);
        require(missing, AiModelClient.class, AiApplicationConfigPort.class);
        if (modes.contains(AiChatMode.SINGLE)) { requireSingle(missing); }
        if (modes.contains(AiChatMode.GROUP)) { requireGroup(missing); }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("AI Starter 所选能力缺少可唯一解析的适配或执行服务：" + String.join(", ", missing));
        }
    }

    /** 单聊原子准备、来源、审计、终态与停止均需要真实宿主实现，禁止用空适配兜底。 */
    private void requireSingle(List<String> missing) {
        require(missing, AiSingleChatStatePort.class, AiInvocationContextPort.class, AiHostExecutionScopePort.class,
                AiSingleChatCompletionPort.class, AiSingleChatPreparePort.class, AiSingleChatBusinessPort.class,
                AiSingleResponseDataPort.class, AiExecutionAuditPort.class, AiMessageOriginPort.class, AiInvocationAuthorizationPort.class,
                AiTransactionExecutor.class, AiSingleChatExecutor.class,
                AiHostSingleChatService.class);
    }

    /** 群聊必须具有目录、同步准备、应用授权、来源审计及门面，只有流组件不算装配齐备。 */
    private void requireGroup(List<String> missing) {
        require(missing, io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupAgentCatalogService.class,
                io.github.yoyocw.aichatkit.ai.starter.host.AiHostGroupAgentService.class);
        require(missing, AiHostExecutionScopePort.class, AiGroupChatStreamStatePort.class,
                AiGroupResponseDataPort.class, AiExecutionAuditPort.class,
                AiGroupChatStreamExecutor.class,
                AiGroupAgentCatalogPort.class, AiGroupChatPreparePort.class, AiInvocationAuthorizationPort.class,
                AiMessageOriginPort.class, AiTransactionExecutor.class,
                AiGroupChatExecutor.class, AiHostGroupChatService.class);
    }

    /** 允许宿主通过 Primary 消歧；失败信息只包含固定类型名，不包含 Bean 值或敏感配置。 */
    private void require(List<String> missing, Class<?>... types) {
        for (Class<?> type : types) {
            try {
                if (beanFactory.getBeanProvider(type).getIfAvailable() == null) { missing.add(type.getSimpleName()); }
            } catch (NoUniqueBeanDefinitionException ex) {
                missing.add(type.getSimpleName() + "（存在多个候选）");
            }
        }
    }
}
