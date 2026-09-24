package example.aienginehost;

import io.github.yoyocw.aichatkit.module.ai.adapter.config.YamlApplicationConfigAdapter;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
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
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianGroupOutputParser;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatExecutionService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiChatStreamEventWriter;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 独立样例的只读装配诊断服务。
 * 依赖列表对应引擎单聊、群聊自动配置；不提供假身份或替代存储，也不触发端口方法。
 */
@Service
@RequiredArgsConstructor
public class HostRuntimeStatusService {

    /** 当前宿主容器，仅查询 Bean 类型元数据，避免诊断主动初始化 FactoryBean。 */
    private final ApplicationContext applicationContext;

    /**
     * 分别检查模型、配置及业务执行层，缺少宿主端口时仍可返回诊断。
     *
     * @return 当前装配快照，不代表权限、事务、数据库或远端模型运行验证结果
     */
    public HostEngineStatusRespVO getStatus() {
        return new HostEngineStatusRespVO(hasBean(BailianClient.class),
                hasBean(YamlApplicationConfigAdapter.class), hasBean(AiApplicationConfigPort.class),
                hasBean(AiChatExecutionService.class), hasBean(AiGroupChatStreamService.class),
                missingSingleDependencies(), missingGroupDependencies());
    }

    /** @return 单聊自动配置及构造器所需、当前未装配的依赖类型 */
    private List<String> missingSingleDependencies() {
        return missingDependencies(AiSingleChatStatePort.class, AiInvocationContextPort.class,
                AiHostExecutionScopePort.class, AiSingleChatCompletionPort.class, AiSingleChatPreparePort.class,
                BailianClient.class, AiApplicationConfigPort.class, BailianProperties.class,
                AiSingleChatBusinessPort.class, AiChatStreamEventWriter.class, AiSingleResponseDataPort.class,
                AiExecutionAuditPort.class, AiMessageOriginPort.class, AiTransactionExecutor.class);
    }

    /** @return 群聊流执行自动配置所需、当前未装配的依赖类型；同步鉴权不属于该流服务 */
    private List<String> missingGroupDependencies() {
        return missingDependencies(AiHostExecutionScopePort.class, AiGroupChatStreamStatePort.class,
                BailianClient.class, BailianGroupOutputParser.class, AiGroupResponseDataPort.class,
                AiExecutionAuditPort.class);
    }

    /**
     * 按固定顺序生成缺失依赖列表，不查询 Bean 内容或执行配置端口的延迟校验。
     *
     * @param types 由样例代码固定的引擎依赖类型，不接受用户传入
     * @return 不可变的缺失类型名称列表
     */
    private List<String> missingDependencies(Class<?>... types) {
        List<String> missing = new ArrayList<>();
        for (Class<?> type : types) {
            if (!hasBean(type)) {
                missing.add(type.getSimpleName());
            }
        }
        return Collections.unmodifiableList(missing);
    }

    /** @param type 要查询的本地类型 @return 是否有类型匹配的 Bean，不主动初始化 FactoryBean */
    private boolean hasBean(Class<?> type) {
        return applicationContext.getBeanNamesForType(type, true, false).length > 0;
    }
}
