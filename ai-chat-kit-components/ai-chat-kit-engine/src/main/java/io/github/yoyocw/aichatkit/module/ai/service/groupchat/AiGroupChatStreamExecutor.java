package io.github.yoyocw.aichatkit.module.ai.service.groupchat;

import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelClient;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelResult;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelException;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelSessionExpiredException;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiExecutionEventSink;
import io.github.yoyocw.aichatkit.ai.engine.execution.AiPreparedExecutions;
import io.github.yoyocw.aichatkit.module.ai.service.execution.AiInvocationGuard;
import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelGroupReply;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;


import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError;
import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMember;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatStreamStatePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatCompletionCommand;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupReplyRecord;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupResponseDataPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_COMPLETED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_GENERATING;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_STOPPED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.AGENT_ORCHESTRATOR;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.AGENT_ORCHESTRATOR_NAME;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiModelExecutionConstants.ERROR_CODE_USER_STOPPED;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_CALL_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.GROUP_WORKFLOW_OUTPUT_INVALID;

/**
 * AI 群聊流式执行服务，负责模型调用、工作流结果落库和 中立事件 事件输出。
 */
@Slf4j
@RequiredArgsConstructor
public class AiGroupChatStreamExecutor {
    /** 异步宿主隔离上下文，不构造登录身份。 */
    private final AiHostExecutionScopePort executionScope;

    /** 宿主负责的群聊状态、完成事务及远端会话保存端口。 */
    private final AiGroupChatStreamStatePort statePort;
    /** 模型 HTTP 客户端。 */
    private final AiModelClient modelClient;
    /** 绑定准备事务的真实提交时刻。 */
    private final AiTransactionExecutor transactions;
    /** 结构化扩展结果服务，负责对白名单群聊结果统一版本化。 */
    private final AiGroupResponseDataPort responseDataService;
    /** 请求级模型执行审计服务。 */
    private final AiExecutionAuditPort executionAuditService;

    /**
     * 创建跨越原请求事务的 中立事件 响应，并在执行线程恢复租户上下文。
     *
     * @param context 当前租户编号
     * @param conversationId 群聊会话编号
     * @param sessionId 本轮远端会话快照，可为空
     * @param userId 登录用户编号
     * @param messageId 生成占位消息编号
     * @param prompt 用户本轮问题
     * @param members 数据库校验后的候选智能体
     * @param historySummary 本地有限历史摘要
     * @param traceCode 服务端生成且已写入执行审计的链路追踪码
     * @param config 本轮数据库应用与工具绑定快照
     * @param authorization 本轮工具凭据，仅传入插件鉴权，不写入提示词或历史
     * @param appChanged 应用变化或旧会话来源未知时通知前端重建了远端会话
     * @return Spring MVC 流式响应体
     */
    public AiPreparedExecution createResponse(AiInvocationContext context, Long conversationId, String sessionId,
                                                Long userId, Long messageId, String prompt,
                                                List<? extends AiGroupMember> members, String historySummary,
                                                String traceCode, AiApplicationConfig config, String authorization,
                                                boolean appChanged) {
        return createResponseForContext(context, conversationId, sessionId, messageId, prompt,
                members, historySummary, traceCode, config, authorization, appChanged);
    }

    /**
     * 通用宿主流入口使用已核验上下文，不要求把用户标识转换为 Long。
     * @param context 已捕获的可信身份
     * @param conversationId 本轮会话编号
     * @param sessionId 已校验应用绑定的远端会话
     * @param messageId 助手占位编号
     * @param prompt 本轮问题
     * @param members 已校验的有序成员
     * @param historySummary 本轮以前的历史
     * @param traceCode 已持久化的审计链路标识
     * @param config 已授权应用配置
     * @param authorization 本轮工具凭据，禁止日志输出
     * @param appChanged 是否重建模型上下文
     * @return 外层事务提交后执行的流响应
     */
    public AiPreparedExecution createResponseForContext(AiInvocationContext context, Long conversationId, String sessionId,
            Long messageId, String prompt, List<? extends AiGroupMember> members, String historySummary,
            String traceCode, AiApplicationConfig config, String authorization, boolean appChanged) {
        List<AiGroupMemberSnapshot> snapshots = new ArrayList<AiGroupMemberSnapshot>();
        for (AiGroupMember member : members) {
            snapshots.add(new AiGroupMemberSnapshot(member.getCode(), member.getName(), member.getRole()));
        }
        return AiPreparedExecutions.prepare(transactions, outputStream -> executionScope.execute(context, () -> streamReply(context, outputStream, conversationId, sessionId,
                messageId, prompt, snapshots, historySummary, traceCode, config, authorization, appChanged)));
    }

    /** 执行群聊生成；数据库终态优先，任何异常路径均在 finally 尝试发送终止事件。 */
    private void streamReply(AiInvocationContext context, AiExecutionEventSink outputStream, Long conversationId, String sessionId,
                             Long messageId, String prompt, List<? extends AiGroupMember> members,
                             String historySummary, String traceCode, AiApplicationConfig config, String authorization,
                             boolean appChanged) {
        long startNanos = System.nanoTime();
        int terminalStatus = STATUS_FAILED;
        String requestId = null;
        Exception failure = null;
        try {
            Integer current = statePort.readStatus(context, messageId);
            if (current == null || current != STATUS_GENERATING) {
                terminalStatus = current == null ? STATUS_FAILED : current;
                return;
            }
            writeEvent(outputStream, "start",
                    eventData("conversationId", conversationId, "messageId", messageId));
            if (appChanged) {
                writeEvent(outputStream, "context-reset", eventData("conversationId", conversationId,
                        "messageId", messageId, "reason", "BAILIAN_APP_CHANGED"));
            }
            writeEvent(outputStream, "progress", eventData("messageId", messageId, "stage", "orchestrating"));
            Map<String, String> agentNames = getAgentNames(members);
            AiModelResult streamResult = invokeWorkflow(context, outputStream, conversationId, sessionId, messageId,
                    prompt, members, historySummary, traceCode, config, authorization);
            validateReplies(streamResult, agentNames);
            String responseData = responseDataService.build(streamResult.getGroupResponseData());
            if (!completeMessages(context, conversationId, messageId, streamResult,
                    responseData, agentNames, config.getAppId())) {
                terminalStatus = currentTerminalStatus(context, messageId);
                return;
            }
            // 事务已经提交，之后的审计或网络写出失败都不能把已完成消息改为失败。
            terminalStatus = STATUS_COMPLETED;
            requestId = streamResult.getRequestId();
            auditQuietly(messageId, traceCode, () -> executionAuditService.complete(context, AiChatMode.GROUP, messageId,
                    streamResult.metrics(elapsedMillis(startNanos))));
            emitReplies(outputStream, messageId, streamResult.getReplies(), agentNames);
            if (StringUtils.hasText(responseData)) {
                writeEvent(outputStream, "result", eventData("messageId", messageId,
                        "responseData", AiEngineJson.parseTree(responseData)));
            }
        } catch (RuntimeException | IOException ex) {
            failure = ex;
            logFailure(messageId, traceCode, failureCode(ex), ex);
            if (terminalStatus != STATUS_COMPLETED) {
                terminalStatus = failGenerating(context, messageId, failureCode(ex), traceCode);
            }
        } finally {
            finishStream(context, outputStream, messageId, terminalStatus, requestId, failure, startNanos, traceCode);
        }
    }

    /** 仅在短期 session 失效时清理标识并恢复一次，其他失败不自动重试。 */
    private AiModelResult invokeWorkflow(AiInvocationContext context, AiExecutionEventSink outputStream, Long conversationId, String sessionId,
                                                Long messageId, String prompt,
                                                List<? extends AiGroupMember> members,
                                                String historySummary, String traceCode, AiApplicationConfig config, String authorization) throws IOException {
        AiModelRequest request = new AiModelRequest(AiChatMode.GROUP, messageId, config.getAppId(),
                prompt, sessionId, null, null, historySummary, traceCode, config, authorization, members);
        try {
            return modelClient.stream(request, event -> { }, () -> stillGenerating(context, messageId));
        } catch (AiModelSessionExpiredException ex) {
            auditQuietly(messageId, traceCode, () -> executionAuditService.incrementRetry(context, AiChatMode.GROUP, messageId));
            statePort.clearSession(context, conversationId, messageId);
            writeEvent(outputStream, "context-reset", eventData("conversationId", conversationId,
                    "messageId", messageId, "reason", "BAILIAN_SESSION_EXPIRED"));
            return modelClient.stream(request.withSessionId(null), event -> { }, () -> stillGenerating(context, messageId));
        }
    }

    /** 在监测线程维持可信隔离，只查询本轮消息；异常由客户端监测器按取消处理。 */
    private boolean stillGenerating(AiInvocationContext context, Long messageId) {
        AtomicBoolean generating = new AtomicBoolean(false);
        executionScope.executeStateRead(context, () -> generating.set(Integer.valueOf(STATUS_GENERATING).equals(
                statePort.readStatus(context, messageId))));
        return generating.get();
    }

    /** Every provider must stay within the prepared member allowlist before persistence. */
    private void validateReplies(AiModelResult result, Map<String, String> memberNames) throws AiModelException {
        if (result.getReplies().isEmpty()) { throw new AiModelException(GROUP_WORKFLOW_OUTPUT_INVALID, false); }
        for (AiModelGroupReply reply : result.getReplies()) {
            if (reply == null || !StringUtils.hasText(reply.getContent())
                    || (!AGENT_ORCHESTRATOR.equals(reply.getSpeakerCode()) && !memberNames.containsKey(reply.getSpeakerCode()))) {
                throw new AiModelException(GROUP_WORKFLOW_OUTPUT_INVALID, false);
            }
        }
    }

    /** 以占位消息的生成态条件更新为入口，事务提交全部成员回复及云端 session。 */
    private boolean completeMessages(AiInvocationContext context, Long conversationId, Long messageId,
                                     AiModelResult streamResult,
                                     String responseData,
                                     Map<String, String> agentNames, String appId) {
        List<AiGroupReplyRecord> replies = new ArrayList<AiGroupReplyRecord>();
        for (AiModelGroupReply reply : streamResult.getReplies()) {
            replies.add(new AiGroupReplyRecord(reply.getSpeakerCode(), getSpeakerName(reply.getSpeakerCode(), agentNames),
                    reply.getContent()));
        }
        return statePort.complete(new AiGroupChatCompletionCommand(context, conversationId, messageId, appId,
                streamResult.getRequestId(), streamResult.getSessionId(), responseData, replies));
    }

    /** 按发言者发送完整文本块，前端按需播放打字动画，避免长回复人为占用请求线程。 */
    private void emitReplies(AiExecutionEventSink outputStream, Long messageId, List<AiModelGroupReply> replies,
                             Map<String, String> agentNames) {
        for (int i = 0; i < replies.size(); i++) {
            AiModelGroupReply reply = replies.get(i);
            int round = i + 1;
            // 同一请求的多条回复使用不同展示标识，避免前端把所有 delta 追加到第一张消息卡。
            String streamMessageId = messageId + "-" + round;
            Map<String, Object> identity = eventData("messageId", streamMessageId, "requestMessageId", messageId,
                    "speakerCode", reply.getSpeakerCode(),
                    "speakerName", getSpeakerName(reply.getSpeakerCode(), agentNames),
                    "round", round);
            writeEvent(outputStream, "speaker-start", identity);
            identity.put("content", reply.getContent());
            writeEvent(outputStream, "delta", identity);
            identity.remove("content");
            writeEvent(outputStream, "speaker-done", identity);
        }
    }

    /** 仅从生成态写入安全失败文案；竞争失败时保留已有终态，数据库异常由 中立事件 独立收口。 */
    private int failGenerating(AiInvocationContext context, Long messageId, AiExecutionError errorCode, String traceCode) {
        try {
            boolean failed = statePort.fail(context, messageId, errorCode.getMsg());
            return failed ? STATUS_FAILED : currentTerminalStatus(context, messageId);
        } catch (RuntimeException ex) {
            logFailure(messageId, traceCode, BAILIAN_CALL_FAILED, ex);
            return STATUS_FAILED;
        }
    }

    /** 按用户归属回读实际终态；记录缺失或仍在生成不能冒充用户已停止。 */
    private int currentTerminalStatus(AiInvocationContext context, Long messageId) {
        Integer current = statePort.readStatus(context, messageId);
        return current == null || current == STATUS_GENERATING ? STATUS_FAILED : current;
    }

    /** 审计失败不得阻止安全 error 与 done，前端总能在连接可写时结束加载态。 */
    private void finishStream(AiInvocationContext context, AiExecutionEventSink outputStream, Long messageId, int status, String requestId,
                              Exception failure, long startNanos, String traceCode) {
        AiExecutionError errorCode = failureCode(failure);
        try {
            if (status == STATUS_STOPPED) {
                executionAuditService.stop(context, AiChatMode.GROUP, messageId, ERROR_CODE_USER_STOPPED);
            } else if (status == STATUS_FAILED) {
                executionAuditService.fail(context, AiChatMode.GROUP, messageId, elapsedMillis(startNanos),
                        String.valueOf(errorCode.getCode()));
            }
        } catch (RuntimeException ex) {
            logFailure(messageId, traceCode, BAILIAN_CALL_FAILED, ex);
        } finally {
            if (status == STATUS_FAILED) {
                writeQuietly(outputStream, "error", eventData("messageId", messageId,
                        "code", errorCode.getCode(), "message", errorCode.getMsg(),
                        "retryable", AiModelException.from(failure).isRetryable()));
            }
            Map<String, Object> terminal = eventData("messageId", messageId, "status", status);
            if (StringUtils.hasText(requestId)) {
                terminal.put("requestId", requestId);
            }
            writeQuietly(outputStream, "done", terminal);
        }
    }

    /** 群聊 JSON 契约错误保留专用分类，其他错误复用公共模型安全分类。 */
    private AiExecutionError failureCode(Exception failure) {
        return AiModelException.from(failure).getErrorCode();
    }

    /** 成功审计及有限重试计数属于辅助记录，不得中断已经落库的回答或 session 恢复。 */
    private void auditQuietly(Long messageId, String traceCode, Runnable audit) {
        try {
            audit.run();
        } catch (RuntimeException ex) {
            logFailure(messageId, traceCode, BAILIAN_CALL_FAILED, ex);
        }
    }

    /** 只记录本地错误码、异常类型和关联标识，避免上游正文、请求参数或凭据进入日志。 */
    private void logFailure(Long messageId, String traceCode, AiExecutionError errorCode, Exception failure) {
        log.error("[streamReply][messageId={} traceCode={} code={} exceptionType={}]",
                messageId, traceCode, errorCode.getCode(), failure.getClass().getSimpleName());
    }

    /** 将本轮群聊执行的单调时钟耗时转换为非负毫秒数。 */
    private long elapsedMillis(long startNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    /**
     * 获取群聊消息展示名称，总控汇总不属于可选群成员，需要单独映射。
     *
     * @param speakerCode 工作流返回的稳定发言者编码
     * @param agentNames 当前群聊成员编码与展示名称映射
     * @return 页面展示名称
     */
    private String getSpeakerName(String speakerCode, Map<String, String> agentNames) {
        return AGENT_ORCHESTRATOR.equals(speakerCode) ? AGENT_ORCHESTRATOR_NAME : agentNames.get(speakerCode);
    }

    private Map<String, String> getAgentNames(List<? extends AiGroupMember> members) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (AiGroupMember member : members) {
            result.put(member.getCode(), member.getName());
        }
        return result;
    }

    private void writeEvent(AiExecutionEventSink outputStream, String event, Map<String, Object> data) {
        // A fresh snapshot allows a non-HTTP sink to retain each immutable event.
        outputStream.accept(event, java.util.Collections.unmodifiableMap(new LinkedHashMap<>(data)));
    }

    private void writeQuietly(AiExecutionEventSink outputStream, String event, Map<String, Object> data) {
        try {
            writeEvent(outputStream, event, data);
        } catch (RuntimeException ignored) {
            // 客户端断开后不能继续写 中立事件；消息终态已在数据库记录。
        }
    }

    private Map<String, Object> eventData(Object... values) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            data.put(String.valueOf(values[i]), values[i + 1]);
        }
        return data;
    }

}
