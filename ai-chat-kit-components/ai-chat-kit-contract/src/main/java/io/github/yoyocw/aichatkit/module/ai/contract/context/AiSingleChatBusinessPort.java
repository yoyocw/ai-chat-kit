package io.github.yoyocw.aichatkit.module.ai.contract.context;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 单聊业务能力与工具授权的宿主边界，模型引擎不持有业务认证实现。 */
public interface AiSingleChatBusinessPort {
    /** @param mapEnabled 本轮展示意图 @param question 本轮问题 @param context 可信身份
     * @return 已授权业务事实与展示快照，不能用页面信息冒充实时数据 */
    AiBusinessSnapshot prepareBusinessContext(boolean mapEnabled, String question, AiInvocationContext context);
    /** @param config 当前应用工具白名单 @param context 可信身份
     * @return 本轮工具Authorization，无工具时可为空；禁止日志/模型正文；失败不得降级固定身份 */
    @Deprecated
    default String getMcpAuthorization(AiApplicationConfig config, AiInvocationContext context) {
        throw new UnsupportedOperationException("Use AiInvocationAuthorizationPort for invocation authorization");
    }
}
