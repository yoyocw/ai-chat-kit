package io.github.yoyocw.aichatkit.module.ai.contract.audit;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import java.time.LocalDateTime;

/** 执行审计持久化边界。同步调用，沿用调用方事务和异常处理，不接收正文或凭据。 */
public interface AiExecutionAuditPort {
    /** 创建执行中记录，参与发送消息的外层事务。
     * @param context 已验证的本轮身份及宿主租户作用域
     * @param chatMode 单聊或群聊模式，不得为空
     * @param conversationId 业务会话编号
     * @param messageId 助手占位消息编号
     * @param traceCode 链路追踪码
     * @param appId 实际调用应用标识
     * @throws IllegalStateException 身份或模式无效；持久化异常交由调用者处理
     */
    void start(AiInvocationContext context, AiChatMode chatMode, Long conversationId, Long messageId,
                      String traceCode, String appId);

    /** 仅将执行中记录更新为完成，已终态记录不覆盖。
     * @param context 已验证的本轮身份及宿主租户作用域
     * @param chatMode 单聊或群聊模式，不得为空
     * @param messageId 助手占位消息编号
     * @param result 不可变用量指标，缺失值保持为空
     * @throws IllegalStateException 身份或模式无效；持久化异常交由调用者处理
     */
    void complete(AiInvocationContext context, AiChatMode chatMode, Long messageId, AiExecutionMetrics result);

    /** 仅将执行中记录更新为失败。
     * @param context 已验证的本轮身份及宿主租户作用域
     * @param chatMode 单聊或群聊模式，不得为空
     * @param messageId 助手占位消息编号
     * @param totalDurationMs 总耗时，单位毫秒
     * @param errorCode 稳定错误编码，不接收异常正文
     * @throws IllegalStateException 身份或模式无效；持久化异常交由调用者处理
     */
    void fail(AiInvocationContext context, AiChatMode chatMode, Long messageId, long totalDurationMs, String errorCode);

    /** 仅停止执行中记录，记录不存在时不操作。
     * @param context 已验证的本轮身份及宿主租户作用域
     * @param chatMode 单聊或群聊模式，不得为空
     * @param messageId 助手占位消息编号
     * @param errorCode 稳定错误编码，不接收异常正文
     * @throws IllegalStateException 身份或模式无效；持久化异常交由调用者处理
     */
    void stop(AiInvocationContext context, AiChatMode chatMode, Long messageId, String errorCode);

    /** 按同一截止时间收口历史执行，参与发送消息的外层事务。
     * @param context 已验证的本轮身份及宿主租户作用域
     * @param chatMode 单聊或群聊模式，不得为空
     * @param conversationId 业务会话编号
     * @param expiredBefore 创建时间截止值，早于该值才收口
     * @param errorCode 稳定错误编码，不接收异常正文
     * @throws IllegalStateException 身份或模式无效；持久化异常交由调用者处理
     */
    void failStale(AiInvocationContext context, AiChatMode chatMode, Long conversationId, LocalDateTime expiredBefore, String errorCode);

    /** 仅对执行中记录原子增加重试次数。
     * @param context 已验证的本轮身份及宿主租户作用域
     * @param chatMode 单聊或群聊模式，不得为空
     * @param messageId 助手占位消息编号
     * @throws IllegalStateException 身份或模式无效；持久化异常交由调用者处理
     */
    void incrementRetry(AiInvocationContext context, AiChatMode chatMode, Long messageId);

}
