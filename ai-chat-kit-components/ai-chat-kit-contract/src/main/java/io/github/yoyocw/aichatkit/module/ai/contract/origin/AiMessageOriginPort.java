package io.github.yoyocw.aichatkit.module.ai.contract.origin;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import java.util.Set;

/** 授权来源持久化边界，与助手占位共用事务；不覆盖、回填或清理来源。 */
public interface AiMessageOriginPort {
    /**
     * 仅为开启记录的可信委托写入一次来源；普通发送保持原行为。
     * @param context 已验证的本轮宿主身份
     * @param mode 助手消息所属单聊或群聊模式
     * @param messageId 本轮助手占位编号，不是群聊后续回复编号
     * @param appId 本轮实际应用编号
     * @throws IllegalStateException 来源或消息归属无效；事务缺失、缺表、重复键等持久化失败必须向外传播
     */
    void record(AiInvocationContext context, AiChatMode mode, Long messageId, String appId);

    /**
     * 在调用方事务内复查不可变消息来源；只验证来源，不替代会话、功能权限或消息状态 CAS。
     * @param context 已认证的当前宿主身份，必须与当前事务租户一致
     * @param caller 认证侧本轮核验的原业务调用方身份，不能使用消费者 AI 身份代替
     * @param mode 由服务端固定入口确定的消息模式
     * @param messageId 正数助手消息编号
     * @param allowedAppIds 认证侧最新核验的调用方应用白名单，不接受请求自报值
     * @return 原来源实际使用的应用编号，仍须在同一事务内核对助手消息并执行状态 CAS
     * @throws IllegalStateException 上下文、来源或白名单不匹配；缺事务及数据库错误向外传播
     */
    String verify(AiInvocationContext context, AiCallerOrigin caller, AiChatMode mode,
                  Long messageId, Set<String> allowedAppIds);
}
