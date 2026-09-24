package io.github.yoyocw.aichatkit.module.ai.contract.config;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 读取部署应用配置；一轮只读取一次，不提供任意配置键查询。 */
public interface AiApplicationConfigPort {
    /** @param context 同轮上下文；部署级实现不据此选择租户配置，身份授权由独立端口负责
     * @param mode 服务入口指定的对话模式
     * @return 校验通过的不可变绑定快照
     * @throws IllegalStateException 配置缺失或非法，不能回退数据库或默认应用 */
    AiApplicationConfig load(AiInvocationContext context, AiChatMode mode);
}
