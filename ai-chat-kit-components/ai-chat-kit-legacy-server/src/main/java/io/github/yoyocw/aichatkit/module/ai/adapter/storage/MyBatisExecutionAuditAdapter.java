package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.execution.AiModelExecutionDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.execution.AiModelExecutionMapper;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionMetrics;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import org.springframework.util.StringUtils;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_COMPLETED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_GENERATING;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_STOPPED;

/**
 * AI 模型执行审计服务，负责创建请求级记录并将百炼指标与业务消息终态统一落库。
 */
@Service
@RequiredArgsConstructor
public class MyBatisExecutionAuditAdapter implements AiExecutionAuditPort {

    /** 模型执行审计数据访问接口。 */
    private final AiModelExecutionMapper executionMapper;
    /** Spring 运行环境，用于生成不含凭据的实际实例标识。 */
    private final Environment environment;

    /**
     * 创建与助手占位消息一一对应的执行中审计记录。
     *
     * @param conversationId 业务会话编号
     * @param messageId 助手占位消息编号
     * @param context 已验证的本轮身份及宿主作用域
     * @param chatMode single 或 group 执行模式
     * @param traceCode 服务端链路追踪码
     * @param appId 实际调用的百炼应用 ID
     */
    @Override
    public void start(AiInvocationContext context, AiChatMode chatMode, Long conversationId, Long messageId,
                      String traceCode, String appId) {
        Long userId = validateContext(context);
        String mode = mode(chatMode);
        executionMapper.insert(AiModelExecutionDO.builder()
                .conversationId(conversationId).messageId(messageId).userId(userId).mode(mode)
                .traceCode(traceCode).appId(appId).status(STATUS_GENERATING).retryCount(0)
                .instanceId(buildInstanceId()).build());
    }

    /**
     * 将成功执行的可观测指标写入审计终态。
     *
     * @param context 已验证的本轮身份及宿主作用域
     * @param chatMode 执行模式
     * @param messageId 助手占位消息编号
     * @param result 平台无关的指标快照，不包含完整响应
     */
    @Override
    public void complete(AiInvocationContext context, AiChatMode chatMode, Long messageId, AiExecutionMetrics result) {
        Long userId = validateContext(context);
        String mode = mode(chatMode);
        executionMapper.updateRunning(userId, AiModelExecutionDO.builder().mode(mode).messageId(messageId)
                .requestId(result.getRequestId()).modelNames(result.getModelNames())
                .inputTokens(result.getInputTokens()).outputTokens(result.getOutputTokens())
                .toolCallCount(result.getToolCallCount()).firstTokenMs(result.getFirstTokenMs())
                .totalDurationMs(result.getTotalDurationMs()).status(STATUS_COMPLETED).build());
    }

    /**
     * 将执行中审计记录收口为失败并保留稳定错误编码。
     *
     * @param context 已验证的本轮身份及宿主作用域
     * @param chatMode 执行模式
     * @param messageId 助手占位消息编号
     * @param totalDurationMs 失败前总耗时，单位毫秒
     * @param errorCode 稳定错误编码
     */
    @Override
    public void fail(AiInvocationContext context, AiChatMode chatMode, Long messageId, long totalDurationMs, String errorCode) {
        Long userId = validateContext(context);
        String mode = mode(chatMode);
        executionMapper.updateRunning(userId, AiModelExecutionDO.builder().mode(mode).messageId(messageId)
                .status(STATUS_FAILED).totalDurationMs(totalDurationMs).errorCode(errorCode).build());
    }

    /**
     * 将执行中审计记录收口为用户停止。
     *
     * @param context 已验证的本轮身份及宿主作用域
     * @param chatMode 执行模式
     * @param messageId 助手占位消息编号
     * @param errorCode 稳定停止原因编码
     */
    @Override
    public void stop(AiInvocationContext context, AiChatMode chatMode, Long messageId, String errorCode) {
        Long userId = validateContext(context);
        String mode = mode(chatMode);
        AiModelExecutionDO running = executionMapper.selectRunning(userId, mode, messageId);
        if (running == null) {
            return;
        }
        long totalDurationMs = Math.max(0L,
                Duration.between(running.getCreateTime(), LocalDateTime.now()).toMillis());
        executionMapper.updateRunning(userId, AiModelExecutionDO.builder().mode(mode).messageId(messageId)
                .status(STATUS_STOPPED).totalDurationMs(totalDurationMs).errorCode(errorCode).build());
    }

    /** 将超过百炼读取超时仍未完成的历史审计批量收口。 */
    @Override
    public void failStale(AiInvocationContext context, AiChatMode chatMode, Long conversationId, LocalDateTime expiredBefore, String errorCode) {
        Long userId = validateContext(context);
        String mode = mode(chatMode);
        executionMapper.failStale(userId, mode, conversationId, expiredBefore, errorCode);
    }

    /** 记录百炼短期会话失效后发生的一次有限重试。 */
    @Override
    public void incrementRetry(AiInvocationContext context, AiChatMode chatMode, Long messageId) {
        Long userId = validateContext(context);
        String mode = mode(chatMode);
        executionMapper.incrementRetry(userId, mode, messageId);
    }

    /** 使用部署环境提供的主机名标识实际处理本轮任务的 Spring 实例。 */
    private String buildInstanceId() {
        String applicationName = environment.getProperty("spring.application.name", "unknown");
        String hostName = environment.getProperty("HOSTNAME");
        if (hostName == null || hostName.trim().isEmpty()) {
            hostName = environment.getProperty("COMPUTERNAME", "unknown");
        }
        return applicationName + "@" + hostName;
    }
    /** 校验宿主建立的租户作用域；不在异步线程重建登录身份。 */
    private Long validateContext(AiInvocationContext context) {
        if (context == null || !"platform".equals(context.getNamespace())
                || !StringUtils.hasText(context.getInvocationId()) || TenantContextHolder.isIgnore()) {
            throw new IllegalStateException("审计上下文无效");
        }
        try {
            Long userId = Long.valueOf(context.getActorId());
            if (!Objects.equals(Long.valueOf(context.getTenantId()), TenantContextHolder.getTenantId())) {
                throw new IllegalStateException();
            }
            return userId;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("审计宿主身份不匹配");
        }
    }

    /** 仅持久化已定义的执行模式，避免空模式放宽查询。 */
    private String mode(AiChatMode mode) {
        if (mode == null) { throw new IllegalStateException("审计模式缺失"); }
        return mode == AiChatMode.SINGLE ? "single" : "group";
    }
}
