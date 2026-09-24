package io.github.yoyocw.aichatkit.ai.starter.host;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupAgentCatalogService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 群聊可用目录授权入口，不开放 HTTP，不接受客户端指定权限或成员职责。 */
public final class AiHostGroupAgentService {
    /** 当前真实会话和独立目录权限校验。 */
    private final AiHostAuthenticationBridge authentication;
    /** 再次匹配身份的目录用例。 */
    private final AiGroupAgentCatalogService catalog;

    /** @param authentication 真实认证桥 @param catalog 内置目录用例 */
    public AiHostGroupAgentService(AiHostAuthenticationBridge authentication, AiGroupAgentCatalogService catalog) {
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /** @return 独立 GROUP_AGENT_LIST 权限核验后可选的真实成员 */
    public List<AiGroupMemberSnapshot> listAvailable(String expectedActorId) {
        AiHostSession session = authentication.authorizeCurrent(expectedActorId, AiHostAction.GROUP_AGENT_LIST);
        if (session.getExpiresAtMillis() <= System.currentTimeMillis()) { throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED); }
        return catalog.listAvailableForContext(new AiInvocationContext(session.getNamespace(), session.getTenantId(),
                session.getActorId(), UUID.randomUUID().toString()));
    }
}
