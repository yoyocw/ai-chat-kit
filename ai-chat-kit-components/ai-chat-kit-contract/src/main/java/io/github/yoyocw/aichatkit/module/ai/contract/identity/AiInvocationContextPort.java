package io.github.yoyocw.aichatkit.module.ai.contract.identity;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

/**
 * 同步宿主身份捕获端口；认证证明由宿主验证，不从客户端反序列化上下文。
 * 已确证失败可抛 AiIdentityException；旧异常按未知核验失败处理，禁止据文案猜测未登录。
 */
public interface AiInvocationContextPort {
    /** 从当前真实认证源捕获身份，不接受请求自报 actor；未适配时明确拒绝。 */
    default AiInvocationContext captureCurrent() {
        throw new AiIdentityException(AiIdentityError.CONFIGURATION);
    }
    /** @param expectedActorId 本轮现有会话归属标识
     * @return 已核验身份及新生成的同轮编号
     * @throws IllegalStateException 身份缺失、不匹配或宿主租户映射无效 */
    AiInvocationContext capture(String expectedActorId);
}
