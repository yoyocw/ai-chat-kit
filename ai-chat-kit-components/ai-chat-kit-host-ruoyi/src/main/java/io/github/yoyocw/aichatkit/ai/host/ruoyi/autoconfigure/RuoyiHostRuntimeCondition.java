package io.github.yoyocw.aichatkit.ai.host.ruoyi.autoconfigure;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/** 关闭时不加载 provided 宿主类；显式启用但缺真实框架时明确拒绝，不静默跳过。 */
public final class RuoyiHostRuntimeCondition implements Condition {
    /** @return 默认关闭可安全留在类路径；启用时必要原宿主类型必须存在 */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!context.getEnvironment().getProperty("ai-chat-kit.ai.ruoyi-host.enabled", Boolean.class, false)) { return true; }
        String[] required = {"com.ruoyi.common.core.domain.model.LoginUser", "com.ruoyi.common.core.redis.RedisCache",
                "com.ruoyi.framework.web.service.TokenService", "com.ruoyi.framework.web.service.SysPermissionService",
                "com.ruoyi.system.service.ISysUserService", "javax.servlet.http.HttpServletRequest",
                "org.springframework.security.core.context.SecurityContextHolder"};
        for (String name : required) {
            if (!ClassUtils.isPresent(name, context.getClassLoader())) { throw new AiIdentityException(AiIdentityError.CONFIGURATION); }
        }
        return true;
    }
}
