package io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionMetrics;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess.SCOPE;
import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository.parameters;

/** PostgreSQL 请求级执行审计；所有终态更新仅影响当前身份的执行中记录。 */
public final class AiJdbcExecutionAuditAdapter implements AiExecutionAuditPort {
    /** 固定数据源及身份参数校验。 */
    private final AiJdbcAccess access;
    /** @param access 与聊天持久化相同的访问对象 */
    public AiJdbcExecutionAuditAdapter(AiJdbcAccess access) { this.access = access; }

    /** @return 是否全链使用指定访问对象，供独立状态事务装配核对同源性 */
    public boolean usesAccess(AiJdbcAccess expected) { return access == expected; }

    /** 审计与助手占位同事务；唯一约束禁止同轮重复记录。 */
    @Override
    public void start(AiInvocationContext context, AiChatMode mode, Long conversationId, Long messageId, String traceCode, String appId) {
        access.requireTransaction();
        access.jdbc().update("INSERT INTO ai_runtime_execution(namespace,tenant_id,actor_id,mode,conversation_id,message_id,trace_code,app_id,status) VALUES(?,?,?,?,?,?,?,?,0)",
                access.scope(context).args(mode(mode), conversationId, messageId, traceCode, appId));
    }

    /** 保留未知指标的 null 值，仅收口执行中的同轮审计。 */
    @Override
    public void complete(AiInvocationContext context, AiChatMode mode, Long messageId, AiExecutionMetrics metrics) {
        access.executor().runRequired(() -> access.jdbc().update("UPDATE ai_runtime_execution SET status=1,request_id=?,model_names=?,input_tokens=?,output_tokens=?,tool_call_count=?,first_token_ms=?,total_duration_ms=?,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND message_id=? AND status=0", parameters(new Object[]{metrics.getRequestId(), metrics.getModelNames(),
                metrics.getInputTokens(), metrics.getOutputTokens(), metrics.getToolCallCount(), metrics.getFirstTokenMs(), metrics.getTotalDurationMs()},
                access.scope(context).args(mode(mode), messageId))));
    }

    /** 失败耗时与固定原因由引擎提供，不保存异常对象或模型原始响应。 */
    @Override
    public void fail(AiInvocationContext context, AiChatMode mode, Long messageId, long duration, String errorCode) {
        access.executor().runRequired(() -> access.jdbc().update("UPDATE ai_runtime_execution SET status=3,total_duration_ms=?,error_code=?,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND message_id=? AND status=0",
                parameters(new Object[]{Math.max(0L, duration), errorCode}, access.scope(context).args(mode(mode), messageId))));
    }

    /** 停止耗时由数据库真实创建时间计算，沿用发送事务的数据源。 */
    @Override
    public void stop(AiInvocationContext context, AiChatMode mode, Long messageId, String errorCode) {
        access.requireTransaction();
        access.jdbc().update("UPDATE ai_runtime_execution SET status=2,error_code=?,total_duration_ms=GREATEST(0,EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-created_at))*1000),updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND message_id=? AND status=0",
                parameters(new Object[]{errorCode}, access.scope(context).args(mode(mode), messageId)));
    }

    /**
     * 兼容旧宿主提供的本地截止时刻，按调用 JVM 时区解释，不能作为跨实例无时区统一时间。
     * 默认 JDBC 单聊和群聊准备不走此入口，均使用数据库时钟按超时秒数同时收口消息及审计。
     */
    @Override
    public void failStale(AiInvocationContext context, AiChatMode mode, Long conversationId, LocalDateTime cutoff, String errorCode) {
        access.executor().runRequired(() -> access.jdbc().update("UPDATE ai_runtime_execution SET status=3,error_code=?,total_duration_ms=GREATEST(0,EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP-created_at))*1000),updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND conversation_id=? AND status=0 AND created_at<?",
                parameters(new Object[]{errorCode}, access.scope(context).args(mode(mode), conversationId, Timestamp.valueOf(cutoff)))));
    }

    /** 只在当前执行中记录一次会话重建尝试。 */
    @Override
    public void incrementRetry(AiInvocationContext context, AiChatMode mode, Long messageId) {
        access.executor().runRequired(() -> access.jdbc().update("UPDATE ai_runtime_execution SET retry_count=retry_count+1,updated_at=CURRENT_TIMESTAMP WHERE "
                + SCOPE + " AND mode=? AND message_id=? AND status=0", access.scope(context).args(mode(mode), messageId)));
    }

    /** @return 固定模式字符串，拒绝缺失模式导致查询扩大 */
    public static String mode(AiChatMode mode) {
        if (mode == null) { throw new IllegalArgumentException("AI 执行模式缺失"); }
        return mode == AiChatMode.SINGLE ? "single" : "group";
    }
}
