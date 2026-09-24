package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

/** 同步请求线程上的可信身份捕获；不读取客户端声明的用户标识。 */
public final class AiWebIdentity {
    /** 宿主真实认证上下文端口。 */
    private final AiInvocationContextPort contextPort;
    /** 固定部署命名空间，不能由HTTP覆盖。 */
    private final String namespace;
    /** @param contextPort 真实端口 @param namespace 部署命名空间 */
    public AiWebIdentity(AiInvocationContextPort contextPort, String namespace) {
        this.contextPort = contextPort;
        this.namespace = namespace;
    }
    /** @return 当前真实用户；随后仍由Starter实时复核会话、权限与归属 */
    public String currentActor() {
        try {
            AiInvocationContext context = contextPort.captureCurrent();
            if (context == null || !namespace.equals(context.getNamespace())
                    || blank(context.getTenantId()) || blank(context.getActorId()) || blank(context.getInvocationId())) {
                throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
            }
            return context.getActorId();
        } catch (AiIdentityException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 无法分类的宿主错误不能推断为登录过期，也不传播异常中的凭据。
            throw new AiIdentityException(AiIdentityError.VERIFICATION_FAILED);
        }
    }
    /** @return 身份维度是否为空；不转换宿主标识的类型或格式 */
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

