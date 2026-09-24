package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;

/** 真实普通用户凭据与独立停止权限核验；MCP工具JWT和固定mcp_均不能作为停止证明。 */
public interface AiStopUserProofPort {
    /**
     * @param userProof 宿主真实普通用户凭据，不是工具凭据
     * @param receiver 部署固定的命名空间和租户 @param mode 服务端固定操作
     * @return 同时通过真实会话和该模式停止权限的用户快照
     * @throws IllegalStateException 身份或停止权限无效，不以发送权限代替
     */
    AiHostSession verify(String userProof, AiStopReceiver receiver, AiChatMode mode);

    /**
     * @param expected 原用户真实会话快照 @param receiver 固定部署接收方 @param mode 原固定操作
     * @return 原session/tenant/actor的当前快照，重新检查当前停止权限，不自动刷新原证明
     * @throws IllegalStateException 已失效、撤权、会话替换或不可核验
     */
    AiHostSession recheck(AiHostSession expected, AiStopReceiver receiver, AiChatMode mode);
}
