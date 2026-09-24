package io.github.yoyocw.aichatkit.module.ai.contract.context;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.List;

/** AI 群聊成员目录；从可信配置或数据库加载，不能接受客户端任意成员职责。 */
public interface AiGroupAgentCatalogPort {
    /** @return 当前已获目录读取权限的身份可用的真实有序目录；未实现不得返回空目录兜底 */
    default List<AiGroupMemberSnapshot> listAvailable(AiInvocationContext context) {
        throw new IllegalStateException("宿主尚未实现群聊目录读取");
    }
    /**
     * @param context 已认证且已授权应用的身份
     * @param codes 用户选择的有序成员编码，A-Z/数字/下划线/短横线，不能重复或使用ORCHESTRATOR
     * @return 同顺序且均启用的不可变成员快照
     * @throws IllegalStateException 成员缺失、停用、无权访问或数量不在2至3之间
     */
    List<AiGroupMemberSnapshot> resolve(AiInvocationContext context, List<String> codes);
}
