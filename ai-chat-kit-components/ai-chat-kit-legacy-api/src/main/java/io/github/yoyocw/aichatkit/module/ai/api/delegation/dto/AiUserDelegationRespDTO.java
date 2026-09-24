package io.github.yoyocw.aichatkit.module.ai.api.delegation.dto;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import lombok.Getter;
import lombok.Setter;

/** 服务身份与用户委托均校验后的入口上下文，不返回原始凭据。 */
@Getter
@Setter
public class AiUserDelegationRespDTO {
    /** 已验证的机器调用方及当前允许目标。 */
    private AiServiceCallerRespDTO caller;
    /** 已验证的用户会话身份，不得替换为机器主体 userId=0。 */
    private OAuth2AccessTokenCheckRespDTO user;
}
