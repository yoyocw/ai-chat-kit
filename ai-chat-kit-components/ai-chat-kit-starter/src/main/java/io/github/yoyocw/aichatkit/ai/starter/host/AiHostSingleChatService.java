package io.github.yoyocw.aichatkit.ai.starter.host;

import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiSingleChatExecutor;

import java.util.Objects;
import java.util.UUID;

/**
 * 嵌入式宿主的单聊授权入口；由宿主已认证的 Controller 或服务调用，不自行开放 HTTP 接口。
 * 引擎重新捕获并核对已授权 namespace/tenant/actor 后才访问配置及存储。
 * 事务、资源归属、工具授权、流式终态仍由引擎处理；本入口不提供 JWT 验签。
 */
public final class AiHostSingleChatService {
    /** 实时核验宿主身份、会话及固定操作权限。 */
    private final AiHostAuthenticationBridge authenticationBridge;
    /** Spring 容器中绑定显式事务执行器的单聊引擎，不能使用手工构造的无事务实例。 */
    private final AiSingleChatExecutor executionService;

    /**
     * @param authenticationBridge 宿主认证边界
     * @param executionService 容器管理的单聊引擎
     * @throws NullPointerException 任一服务缺失
     */
    public AiHostSingleChatService(AiHostAuthenticationBridge authenticationBridge,
                                  AiSingleChatExecutor executionService) {
        this.authenticationBridge = Objects.requireNonNull(authenticationBridge, "宿主认证桥接不能为空");
        this.executionService = Objects.requireNonNull(executionService, "单聊引擎不能为空");
    }

    /**
     * 授权发送并完成引擎同步准备；宿主在事务提交后消费中立执行事件。
     * @param request 用户问题及会话参数，由引擎继续校验及检查资源归属
     * @param expectedActorId 仅用于与真实登录用户核对的预期标识，不提供登录能力
     * @return 准备完成的中立执行，沿用其可信作用域和异常收口
     * @throws IllegalStateException 认证失败或授权与执行身份不一致
     */
    public AiPreparedExecution send(AiSingleChatRequest request, String expectedActorId) {
        return prepare(request, expectedActorId);
    }

    /** Authorize and prepare synchronously; consume only after the actual transaction commit. */
    public AiPreparedExecution prepare(AiSingleChatRequest request, String expectedActorId) {
        AiHostSession session = authenticationBridge.authorizeCurrent(expectedActorId, AiHostAction.CHAT_SEND);
        return executionService.sendMessageForContext(request, expectedContext(session));
    }

    /**
     * 授权停止用户消息，消息来源、状态及归属仍由引擎和宿主存储适配器检查。
     * @param messageId 正数助手消息编号
     * @param expectedActorId 仅用于与真实登录用户核对的预期标识
     * @throws IllegalArgumentException 消息编号无效
     * @throws IllegalStateException 认证失败或授权与执行身份不一致
     */
    public void stop(Long messageId, String expectedActorId) {
        if (messageId == null || messageId <= 0) { throw new IllegalArgumentException("消息编号无效"); }
        AiHostSession session = authenticationBridge.authorizeCurrent(expectedActorId, AiHostAction.CHAT_STOP);
        executionService.stopMessageForContext(messageId, expectedContext(session));
    }

    /** 已认证身份仅作为引擎匹配条件；引擎重新可信捕获实际执行的调用编号。 */
    private AiInvocationContext expectedContext(AiHostSession session) {
        if (session.getExpiresAtMillis() <= System.currentTimeMillis()) {
            throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED);
        }
        return new AiInvocationContext(session.getNamespace(), session.getTenantId(), session.getActorId(),
                UUID.randomUUID().toString());
    }
}
