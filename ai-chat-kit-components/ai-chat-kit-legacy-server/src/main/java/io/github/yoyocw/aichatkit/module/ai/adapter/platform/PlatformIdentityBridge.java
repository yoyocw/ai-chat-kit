package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import java.util.UUID;
import java.util.Objects;

/** 林业同步入口身份桥接；仅从认证上下文取值，不接收浏览器声明的命名空间。 */
@Component
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.platform-host", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class PlatformIdentityBridge implements AiInvocationContextPort {
    /** 当前真实登录身份仍经原 capture 的租户和用户匹配，不从请求读取用户编号。 */
    @Override
    public AiInvocationContext captureCurrent() {
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (actor == null || actor.getId() == null) { throw new IllegalStateException("当前登录身份缺失"); }
        return capture(actor.getId().toString());
    }
    /** @param expectedActorId 本轮会话归属用户
     * @return 不含凭据的已核对身份快照
     * @throws IllegalStateException 身份缺失、归属不符或租户不一致，在写消息前拒绝 */
    @Override
    public AiInvocationContext capture(String expectedActorId) {
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (actor == null || actor.getId() == null || actor.getTenantId() == null
                || !actor.getId().toString().equals(expectedActorId)
                || !Objects.equals(actor.getTenantId(), TenantContextHolder.getTenantId())) {
            throw new IllegalStateException("当前对话身份与会话归属不一致");
        }
        // 宿主用户/租户本身为 Long，捕获时生成规范字符串，不延迟到异步才发现映射错误。
        return new AiInvocationContext("platform", actor.getTenantId().toString(), actor.getId().toString(), UUID.randomUUID().toString());
    }
}
