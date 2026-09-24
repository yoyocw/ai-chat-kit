package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.AiServiceBindingDTO;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 仅在同步发送准备阶段存在的可信委托，不可序列化到消息或日志。 */
@Getter
@RequiredArgsConstructor
public class AiDelegatedCallContext {
    /** 认证侧验证后的目标绑定。 */
    private final AiServiceBindingDTO binding;
    /** 本轮完整工具 Authorization；只传入百炼鉴权参数。 */
    private final String authorization;
    /** 记录开启时从认证响应复制的来源；不得从请求参数生成。 */
    private final AiCallerOrigin origin;
}
