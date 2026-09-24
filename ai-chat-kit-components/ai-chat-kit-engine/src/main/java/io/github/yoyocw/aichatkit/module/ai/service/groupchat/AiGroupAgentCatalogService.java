package io.github.yoyocw.aichatkit.module.ai.service.groupchat;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupAgentCatalogPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Objects;

/** 独立目录读取用例，授权后重新捕获身份，不借发送权限或空目录冒充可用能力。 */
@RequiredArgsConstructor
public final class AiGroupAgentCatalogService {
    /** 真实同步身份源。 */
    private final AiInvocationContextPort identities;
    /** 已部署或按身份裁剪的真实成员目录。 */
    private final AiGroupAgentCatalogPort catalog;

    /** @param expected 已通过目录权限核验的身份 @return 该身份可选成员 */
    public List<AiGroupMemberSnapshot> listAvailableForContext(AiInvocationContext expected) {
        if (expected == null) { throw new IllegalArgumentException("群聊目录授权上下文缺失"); }
        AiInvocationContext actual = identities.capture(expected.getActorId());
        if (actual == null || !Objects.equals(actual.getNamespace(), expected.getNamespace())
                || !Objects.equals(actual.getTenantId(), expected.getTenantId())
                || !Objects.equals(actual.getActorId(), expected.getActorId())) {
            throw new IllegalStateException("群聊目录授权前后身份不一致");
        }
        List<AiGroupMemberSnapshot> result = catalog.listAvailable(actual);
        if (result == null) { throw new IllegalStateException("群聊目录返回结果缺失"); }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<AiGroupMemberSnapshot>(result));
    }
}
