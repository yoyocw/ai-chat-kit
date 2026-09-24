package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.enums.UserTypeEnum;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.function.Consumer;

/** 已核验停止身份的同步宿主作用域；调用方必须先验证认证侧响应，不接受浏览器自报身份。 */
@Component
@RequiredArgsConstructor
public class PlatformStopScopeAdapter {
    /** 从新建立的宿主上下文再次捕获本轮身份。 */
    private final AiInvocationContextPort contextPort;

    /**
     * 在原用户的严格租户作用域执行一次本地阶段，成功或异常均恢复原上下文。
     * @param caller 认证侧已验证的原业务调用方
     * @param action 本地短事务预检或消费后的提交，不得放入远程调用
     */
    public void execute(AiCallerOrigin caller, Consumer<AiInvocationContext> action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        Long tenantId = TenantContextHolder.getTenantId();
        boolean ignored = TenantContextHolder.isIgnore();
        try {
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            TenantContextHolder.setTenantId(caller.getTenantId());
            TenantContextHolder.setIgnore(false);
            LoginUser user = new LoginUser();
            user.setId(caller.getUserId());
            user.setTenantId(caller.getTenantId());
            user.setUserType(UserTypeEnum.ADMIN.getValue());
            user.setInfo(Collections.emptyMap());
            // 不改写Servlet请求审计属性，避免残留伪装的原用户身份。
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
            action.accept(contextPort.capture(String.valueOf(caller.getUserId())));
        } finally {
            SecurityContextHolder.setContext(previous);
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(ignored);
        }
    }
}
