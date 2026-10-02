package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelResult;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelException;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelSessionExpiredException;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiExecutionEventSink;
import io.github.yoyocw.aichatkit.ai.engine.execution.AiPreparedExecutions;
import io.github.yoyocw.aichatkit.module.ai.service.execution.AiInvocationGuard;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelEvent;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelEventType;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;
import io.github.yoyocw.aichatkit.module.ai.service.execution.AiChatEvents;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;


import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatBusinessPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatState;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStopResult;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatFailureCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPrepareCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatCompletionCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_COMPLETED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_GENERATING;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_STOPPED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiModelExecutionConstants.ERROR_CODE_USER_STOPPED;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_RESPONSE_INCOMPLETE;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_CONFIG_INVALID;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.CHAT_MESSAGE_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.CHAT_MESSAGE_NOT_GENERATING;

/**
 * AI 单聊执行服务，负责发送前原子准备、模型流式调用、消息终态落库和停止生成。
 */
@Slf4j
@RequiredArgsConstructor
public class AiSingleChatExecutor {

    /** 单聊运行状态、停止、失败与失效会话清理端口。 */
    private final AiSingleChatStatePort statePort;
    /** 同步可信身份捕获端口。 */
    private final AiInvocationContextPort contextPort;
    /** 异步宿主租户隔离端口。 */
    private final AiHostExecutionScopePort executionScope;
    /** 单聊完成与远端会话保存的原子提交端口。 */
    private final AiSingleChatCompletionPort completionPort;
    /** 加入外层发送事务的原子准备存储步骤。 */
    private final AiSingleChatPreparePort preparePort;
    /** 模型流式客户端，负责实际模型调用和本机任务取消。 */
    private final AiModelClient modelClient;
    /** 每轮数据库应用配置。 */
    private final AiApplicationConfigPort singleConfigService;
    /** 模型读取超时与 MCP 应用配置。 */
    private final AiExecutionPolicy policy;
    /** 单聊业务上下文服务，提供权限内地图事实、历史摘要和 MCP 凭据。 */
    private final AiSingleChatBusinessPort businessContextService;
    /** 每轮独立应用授权，不能由业务快照扩展替代。 */
    private final AiInvocationAuthorizationPort authorization;
    /** 结构化扩展结果服务，确保数据库和 中立事件 只使用服务端白名单协议。 */
    private final AiSingleResponseDataPort responseDataService;
    /** 请求级模型执行审计服务。 */
    private final AiExecutionAuditPort executionAuditService;
    /** 与助手占位同事务记录可信委托来源，失败不得发送模型请求。 */
    private final AiMessageOriginPort originPort;
    /** 明确同源事务能力；生产装配必须提供。 */
    private final AiTransactionExecutor transactions;


    /** @return 生产真实事务绑定，旧裸构造不能绕过guard */
    private AiTransactionExecutor transaction() {
        if (transactions == null) { throw new IllegalStateException("AI执行缺少显式事务绑定"); }
        return transactions;
    }

    /**
     * 创建本地消息占位并返回异步流式执行体；事务仅覆盖发送前的本地原子准备。
     *
     * @param reqVO 用户问题、会话编号和地图开关
     * @param userId 当前登录用户编号
     * @return Spring MVC 流式响应体
     * @throws io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException 配置无效、会话无权访问或已有生成任务时抛出
     */
    public AiPreparedExecution sendMessage(AiSingleChatRequest reqVO, Long userId) {
        return sendMessageForActor(reqVO, userId == null ? null : userId.toString());
    }

    /**
     * 使用宿主不透明用户标识发送；标识只用于匹配已认证上下文，不构成认证证明。
     * @param reqVO 本轮请求
     * @param actorId 宿主已认证用户标识，可为空由宿主捕获并核对
     * @return 外层事务提交后执行的 中立事件 响应
     */
    public AiPreparedExecution sendMessageForActor(AiSingleChatRequest reqVO, String actorId) {
        return transaction().required(() -> {
            return sendPrepared(reqVO, actorId, null);
        });
    }

    /**
     * 再次捕获真实身份并核对授权阶段的完整归属，之后才访问配置或存储。
     * @param reqVO 本轮问题请求
     * @param expectedContext 已授权阶段的归属快照，不是认证凭据
     * @return 发送事务提交后执行的响应体
     */
    public AiPreparedExecution sendMessageForContext(AiSingleChatRequest reqVO, AiInvocationContext expectedContext) {
        return transaction().required(() -> {
            if (expectedContext == null) { throw new IllegalArgumentException("授权上下文缺失"); }
            return sendPrepared(reqVO, expectedContext.getActorId(), expectedContext);
        });
    }

    /** 统一发送路径，由外层公开入口建立真实事务。 */
    private AiPreparedExecution sendPrepared(AiSingleChatRequest reqVO, String actorId, AiInvocationContext expectedContext) {
        if (reqVO == null || !StringUtils.hasText(reqVO.getContent()) || reqVO.getContent().length() > 10000
                || (reqVO.getConversationId() != null && reqVO.getConversationId() <= 0)) {
            throw new IllegalArgumentException("单聊请求无效");
        }
        String prompt = reqVO.getContent().trim();
        AiInvocationContext context = captureAndMatch(actorId, expectedContext);
        AiApplicationConfig config = singleConfigService.load(context, AiChatMode.SINGLE);
        if (!modelClient.isConfigured(AiChatMode.SINGLE, config.getAppId())) {
            throw new AiExecutionException(BAILIAN_CONFIG_INVALID);
        }
        String mcpAuthorization = AiInvocationGuard.authorize(authorization, context, config, AiChatMode.SINGLE);
        AiSingleChatPreparedTurn prepared = preparePort.prepare(new AiSingleChatPrepareCommand(context,
                reqVO.getConversationId(), reqVO.getContent(), Boolean.TRUE.equals(reqVO.getMapEnabled()),
                config.getAppId(), policy.getStaleGenerationSeconds()));
        originPort.record(context, AiChatMode.SINGLE, prepared.getAssistantMessageId(), config.getAppId());
        String historySummary = prepared.getHistorySummary();
        Long messageId = prepared.getAssistantMessageId();
        boolean mapEnabled = Boolean.TRUE.equals(reqVO.getMapEnabled());
        AiBusinessSnapshot businessSnapshot = businessContextService.prepareBusinessContext(
                mapEnabled, prompt, context);
        String traceCode = UUID.randomUUID().toString().replace("-", "");
        executionAuditService.start(context, AiChatMode.SINGLE, prepared.getConversationId(), messageId,
                traceCode, config.getAppId());
        return AiPreparedExecutions.prepare(transaction(), outputStream -> executionScope.execute(context,
                () -> streamReply(outputStream, prepared, actorId, messageId,
                        prompt, mapEnabled, historySummary, businessSnapshot,
                        mcpAuthorization, traceCode, config, prepared.isAppChanged(), context)));
    }

    /**
     * 停止当前用户仍处于生成态的助手消息，并取消本实例中的模型 HTTP 调用。
     *
     * @param messageId 助手消息编号
     * @param userId 当前登录用户编号
     * @throws io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException 消息不存在或已经进入终态时抛出
     */
    public void stopMessage(Long messageId, Long userId) {
        stopMessageForActor(messageId, userId == null ? null : userId.toString());
    }

    /** @param messageId 助手消息编号 @param actorId 已认证宿主用户标识，仍由身份端口核对 */
    public void stopMessageForActor(Long messageId, String actorId) {
        transaction().runRequired(() -> {
            stopPrepared(messageId, actorId, null);
        });
    }

    /** @param messageId 助手占位编号 @param expectedContext 已授权阶段的完整归属快照 */
    public void stopMessageForContext(Long messageId, AiInvocationContext expectedContext) {
        transaction().runRequired(() -> {
            if (expectedContext == null) { throw new IllegalArgumentException("授权上下文缺失"); }
            stopPrepared(messageId, expectedContext.getActorId(), expectedContext);
        });
    }

    /** 停止授权前后必须仍属于同一命名空间、租户和用户。 */
    private void stopPrepared(Long messageId, String actorId, AiInvocationContext expectedContext) {
        transaction().requireActive();
        AiInvocationContext context = captureAndMatch(actorId, expectedContext);
        AiSingleChatStopResult result = statePort.stop(context, messageId);
        if (result == AiSingleChatStopResult.NOT_FOUND) {
            throw new AiExecutionException(CHAT_MESSAGE_NOT_EXISTS);
        }
        if (result != AiSingleChatStopResult.STOPPED) {
            throw new AiExecutionException(CHAT_MESSAGE_NOT_GENERATING);
        }
        transaction().afterCommit(() -> modelClient.cancel(AiChatMode.SINGLE, messageId));
        auditQuietly(messageId, () -> executionAuditService.stop(context, AiChatMode.SINGLE, messageId, ERROR_CODE_USER_STOPPED));
    }

    /** 新上下文保留新的 invocationId，仅比较授权归属，避免授权后身份切换。 */
    private AiInvocationContext captureAndMatch(String actorId, AiInvocationContext expected) {
        return AiInvocationGuard.capture(contextPort, actorId, expected);
    }

    /** 执行模型流式调用，将安全事件写给客户端并收口消息终态。 */
    private void streamReply(AiExecutionEventSink outputStream, AiSingleChatPreparedTurn prepared, String actorId,
                             Long messageId, String prompt, boolean mapEnabled, String historySummary,
                             AiBusinessSnapshot businessSnapshot,
                             String mcpAuthorization, String traceCode, AiApplicationConfig config, boolean appChanged, AiInvocationContext context) {
        long startNanos = System.nanoTime();
        StringBuilder partialContent = new StringBuilder();
        try {
            // 将读取占位状态和首帧写出也纳入异常收口，防止尚未调用模型就留下生成态。
            AiSingleChatState assistant = statePort.readState(context, messageId);
            if (assistant != AiSingleChatState.GENERATING) {
                writeTerminal(outputStream, messageId, toMessageStatus(assistant),
                        AiModelException.from(null));
                return;
            }
            AiChatEvents.write(outputStream, "start",
                    AiChatEvents.data("conversationId", prepared.getConversationId(), "messageId", messageId));
            AiChatEvents.writeProgress(outputStream, messageId, "preparing", "正在准备对话上下文", null);
            if (appChanged) {
                AiChatEvents.writeProgress(outputStream, messageId, "preparing",
                        "应用配置已更新，已重建云端会话，本地聊天记录保留", null);
            }
            AiModelRequest modelRequest = new AiModelRequest(AiChatMode.SINGLE, messageId, config.getAppId(),
                    prompt, prepared.getRemoteSessionId(), mapEnabled, businessSnapshot.getPromptFactsJson(),
                    historySummary, traceCode, config, mcpAuthorization, java.util.Collections.emptyList());
            AiChatEvents.writeProgress(outputStream, messageId, "processing", "正在等待智能体处理", null);
            AiModelResult result = invokeModel(
                    outputStream, prepared, actorId, messageId, modelRequest, partialContent, context);
            String responseData = responseDataService.mergeSources(
                    responseDataService.renderBusinessPresentation(messageId, businessSnapshot.getPresentation()),
                    result.getResponseData());
            if (!completionPort.complete(new AiSingleChatCompletionCommand(context, prepared.getConversationId(), messageId,
                    config.getAppId(), result.getContent(), result.getRequestId(),
                    responseData, result.getSessionId()))) {
                AiSingleChatState current = statePort.readState(context, messageId);
                writeTerminal(outputStream, messageId,
                        current == AiSingleChatState.GENERATING ? STATUS_FAILED : toMessageStatus(current),
                        AiModelException.from(null));
                return;
            }
            auditQuietly(messageId, () -> executionAuditService.complete(context, AiChatMode.SINGLE, messageId,
                    result.metrics(elapsedMillis(startNanos))));
            if (StringUtils.hasText(responseData)) {
                AiChatEvents.write(outputStream, "result", AiChatEvents.data("messageId", messageId,
                        "responseData", AiEngineJson.parseTree(responseData)));
            }
            AiChatEvents.write(outputStream, "done", AiChatEvents.data(
                    "messageId", messageId, "status", STATUS_COMPLETED, "requestId", result.getRequestId()));
        } catch (RuntimeException ex) {
            handleStreamFailure(outputStream, messageId, actorId, ex, partialContent.toString(), startNanos, context);
        } catch (IOException ex) {
            handleStreamFailure(outputStream, messageId, actorId, ex, partialContent.toString(), startNanos, context);
        }
    }

    /** 调用模型并在云端短期会话失效时清理本地标识后重试一次。 */
    private AiModelResult invokeModel(AiExecutionEventSink outputStream, AiSingleChatPreparedTurn prepared,
                                               String actorId, Long messageId, AiModelRequest modelRequest,
                                               StringBuilder partialContent, AiInvocationContext context) throws IOException {
        Consumer<AiModelEvent> consumer = event -> {
            // 保留已收到的正文，失败后刷新页面仍可查看部分结果；不混入故障文案。
            if (event.getType() == AiModelEventType.DELTA) {
                partialContent.append(event.getContent());
            }
            AiChatEvents.writeModelEvent(outputStream, messageId, event);
        };
        // 监测线程不继承HTTP身份，只在已经验证的调用上下文中读取本消息已提交状态。
        BooleanSupplier stillGenerating = () -> {
            AtomicBoolean generating = new AtomicBoolean();
            executionScope.executeStateRead(context, () -> generating.set(
                    statePort.readState(context, messageId) == AiSingleChatState.GENERATING));
            return generating.get();
        };
        try {
            return modelClient.stream(modelRequest, consumer, stillGenerating);
        } catch (AiModelSessionExpiredException ex) {
            if (partialContent.length() > 0) {
                throw new AiModelException(BAILIAN_RESPONSE_INCOMPLETE, true);
            }
            auditQuietly(messageId, () -> executionAuditService.incrementRetry(context, AiChatMode.SINGLE, messageId));
            statePort.clearExpiredSession(context, prepared.getConversationId(), messageId);
            AiChatEvents.write(outputStream, "context-reset", AiChatEvents.data(
                    "conversationId", prepared.getConversationId(), "messageId", messageId,
                    "reason", "BAILIAN_SESSION_EXPIRED"));
            return modelClient.stream(modelRequest.withSessionId(null), consumer, stillGenerating);
        }
    }

    /** 将模型或响应写出异常收口为数据库失败终态和安全 中立事件 错误。 */
    private void handleStreamFailure(AiExecutionEventSink outputStream, Long messageId, String actorId, Throwable cause,
                                     String partialContent, long startNanos, AiInvocationContext context) {
        AiModelException failure = AiModelException.from(cause);
        int status = STATUS_FAILED;
        try {
            AiSingleChatState current = statePort.fail(new AiSingleChatFailureCommand(
                    context, messageId, partialContent, failure.getMessage()));
            status = current == AiSingleChatState.GENERATING ? STATUS_FAILED : toMessageStatus(current);
        } catch (RuntimeException persistenceFailure) {
            log.error("[handleStreamFailure][保存失败终态异常 messageId={} type={}]",
                    messageId, persistenceFailure.getClass().getSimpleName());
        } finally {
            // 数据库不可用也要尽力通知前端；已断开的连接由前端 EOF/finally 兜底。
            writeTerminal(outputStream, messageId, status, failure);
        }
        if (status == STATUS_FAILED) {
            auditQuietly(messageId, () -> executionAuditService.fail(context, AiChatMode.SINGLE, messageId,
                    elapsedMillis(startNanos), String.valueOf(failure.getErrorCode().getCode())));
        } else if (status == STATUS_STOPPED) {
            auditQuietly(messageId, () -> executionAuditService.stop(context, AiChatMode.SINGLE, messageId, ERROR_CODE_USER_STOPPED));
        }
        log.warn("[streamReply][流式调用结束 messageId={} status={} code={} type={}]",
                messageId, status, failure.getErrorCode().getCode(), cause.getClass().getSimpleName());
    }

    /** 仅在响应边界映射既有整数协议；缺失记录沿用失败兜底。 */
    private int toMessageStatus(AiSingleChatState state) {
        if (state == null) { throw new IllegalStateException("缺少消息状态"); }
        switch (state) {
            case GENERATING: return STATUS_GENERATING;
            case COMPLETED: return STATUS_COMPLETED;
            case STOPPED: return STATUS_STOPPED;
            case FAILED:
            case MISSING: return STATUS_FAILED;
            default: throw new IllegalStateException("未知消息状态");
        }
    }

    /** 输出安全故障说明及统一终态，error 与 done 都允许前端独立停止等待。 */
    private void writeTerminal(AiExecutionEventSink outputStream, Long messageId, int status, AiModelException failure) {
        if (status == STATUS_FAILED) {
            AiChatEvents.writeQuietly(outputStream, "error", AiChatEvents.data(
                    "messageId", messageId, "code", failure.getErrorCode().getCode(),
                    "message", failure.getMessage(), "retryable", failure.isRetryable()));
        }
        AiChatEvents.writeQuietly(outputStream, "done",
                AiChatEvents.data("messageId", messageId, "status", status));
    }

    /** 审计异常不能阻断已完成的正文输出、用户停止或故障通知。 */
    private void auditQuietly(Long messageId, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException auditFailure) {
            log.error("[auditQuietly][更新执行审计异常 messageId={} type={}]",
                    messageId, auditFailure.getClass().getSimpleName());
        }
    }

    /** 将本轮单聊执行的单调时钟耗时转换为非负毫秒数。 */
    private long elapsedMillis(long startNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

}
