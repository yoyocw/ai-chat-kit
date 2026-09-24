package io.github.yoyocw.aichatkit.module.ai.adapter.config;

import io.github.yoyocw.aichatkit.module.ai.config.AiGroupAgentProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiGroupAgentsProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** 从可信部署配置冻结成员目录；空目录、停用成员或重复编码均不生成默认成员。 */
public final class YamlGroupAgentCatalogAdapter implements AiGroupAgentCatalogPort {
    /** 已启用且通过边界校验的不可变目录快照。 */
    private final Map<String, AiGroupMemberSnapshot> agents;

    /** 检查配置边界；非空字段和成员编码重复问题应在装配时暴露。 */
    public YamlGroupAgentCatalogAdapter(AiGroupAgentsProperties properties) {
        if (properties == null || properties.getAgents() == null) { throw new IllegalStateException("AI 群聊成员配置缺失"); }
        Map<String, AiGroupMemberSnapshot> result = new LinkedHashMap<String, AiGroupMemberSnapshot>();
        HashSet<String> codes = new HashSet<String>();
        for (AiGroupAgentProperties agent : properties.getAgents()) {
            if (agent == null || agent.getCode() == null || !agent.getCode().matches("[A-Z0-9_-]{1,128}")
                    || "ORCHESTRATOR".equals(agent.getCode()) || !codes.add(agent.getCode())) {
                throw new IllegalStateException("AI 群聊成员编码须为大写字母、数字、下划线或短横线，且不得重复或使用ORCHESTRATOR");
            }
            if (!agent.isEnabled()) { continue; }
            if (!valid(agent.getName(), 256) || !valid(agent.getRole(), 4000)) {
                throw new IllegalStateException("AI 群聊成员名称或职责无效");
            }
            result.put(agent.getCode(), new AiGroupMemberSnapshot(agent.getCode(), agent.getName(), agent.getRole()));
        }
        if (result.size() < 2) { throw new IllegalStateException("AI 群聊目录至少需要两个已启用成员"); }
        agents = Collections.unmodifiableMap(result);
    }

    /** 发送时按用户成员顺序重新核对可信目录，不使用请求中自报名称或职责。 */
    @Override
    public List<AiGroupMemberSnapshot> resolve(AiInvocationContext context, List<String> codes) {
        validateContext(context);
        if (context == null || codes == null || codes.size() < 2 || codes.size() > 3
                || new HashSet<String>(codes).size() != codes.size()) {
            throw new IllegalStateException("AI 群聊成员必须为2至3个且不重复");
        }
        List<AiGroupMemberSnapshot> result = new ArrayList<AiGroupMemberSnapshot>();
        for (String code : codes) {
            AiGroupMemberSnapshot member = agents.get(code);
            if (member == null) { throw new IllegalStateException("AI 群聊成员未启用或不存在"); }
            result.add(member);
        }
        return Collections.unmodifiableList(result);
    }

    /** 部署级共享目录不附加个人成员权限；上游必须已检查独立目录读取权限。 */
    @Override
    public List<AiGroupMemberSnapshot> listAvailable(AiInvocationContext context) {
        validateContext(context);
        return Collections.unmodifiableList(new ArrayList<AiGroupMemberSnapshot>(agents.values()));
    }

    /** 这里只验证结构，不把身份 DTO 当认证或权限证明。 */
    private void validateContext(AiInvocationContext context) {
        if (context == null || !valid(context.getNamespace(), 128) || !valid(context.getTenantId(), 128)
                || !valid(context.getActorId(), 128) || context.getInvocationId() == null
                || context.getInvocationId().trim().isEmpty()) { throw new IllegalStateException("群聊目录身份无效"); }
    }

    /** 检查部署输入长度，不修改有意义的标识和职责内容。 */
    private static boolean valid(String value, int limit) { return value != null && !value.trim().isEmpty() && value.length() <= limit; }
}
