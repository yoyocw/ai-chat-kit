package example.aienginehost;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** 宿主接入诊断结果，只表达本地装配，不包含配置值、用户身份或业务数据。 */
@Getter
@RequiredArgsConstructor
public class HostEngineStatusRespVO {

    /** 本进程模型客户端是否已装配；不代表外部模型连接或凭据已验证。 */
    private final boolean modelClientAssembled;

    /** 默认 YAML 配置适配器是否已装配；不代表应用和工具标识有效。 */
    private final boolean yamlConfigPortAssembled;

    /** 任意应用配置端口是否已装配，包含宿主替换 YAML 的实现。 */
    private final boolean applicationConfigPortAssembled;

    /** 单聊执行服务是否已装配，不代表宿主身份和事务实现已通过验收。 */
    private final boolean singleExecutionAssembled;

    /** 群聊流执行服务是否已装配，同步权限校验和消息准备仍由宿主负责。 */
    private final boolean groupExecutionAssembled;

    /** 单聊装配缺少的依赖类型名；顺序固定，空列表不替代业务验收。 */
    private final List<String> missingSingleDependencies;

    /** 群聊流执行装配缺少的依赖类型名；存储端口自身须保证事务。 */
    private final List<String> missingGroupDependencies;

    /**
     * 当前样例未提供认证调用入口，也不执行真实身份、事务或模型验收。
     *
     * @return 始终为 false，避免将宿主启动或组件齐备误报为完整 AI 业务就绪
     */
    public boolean isBusinessReady() {
        return false;
    }

    /** @return 固定说明，不回显外部配置、异常或凭据 */
    public String getReadinessNote() {
        return "仅检查本地装配；缺失依赖时未就绪。即使组件齐备，仍需宿主提供认证入口并验证身份、事务、存储和模型调用。";
    }
}
