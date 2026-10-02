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
import io.github.yoyocw.aichatkit.module.ai.config.AiExecutionPolicy;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationPort;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.authorization.AiInvocationAuthorizationResult;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfigPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparedTurn;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiSingleChatStopResult;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.*;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiModelExecutionConstants.ERROR_CODE_USER_STOPPED;

/** 群聊完整同步用例：可信身份、真实授权、原子准备、来源审计和模型流衔接。 */
@RequiredArgsConstructor
public class AiGroupChatExecutor {
    /** 从当前宿主真实认证上下文再次捕获身份。 */
    private final AiInvocationContextPort identities;
    /** 已校验应用绑定配置。 */
    private final AiApplicationConfigPort applications;
    /** 每轮真实应用及工具授权，不因无工具而跳过。 */
    private final AiInvocationAuthorizationPort authorization;
    /** AI 自有会话成员、准备及停止存储。 */
    private final AiGroupChatPreparePort preparation;
    /** 已实现生成状态读取、完成事务及安全 中立事件 的流服务。 */
    private final AiGroupChatStreamExecutor stream;
    /** 本实例模型调用及取消能力。 */
    private final AiModelClient client;
    /** 读取超时用于失联占位清理阈值。 */
    private final AiExecutionPolicy policy;
    /** 与助手占位同事务创建的执行审计。 */
    private final AiExecutionAuditPort audit;
    /** 与助手占位同事务记录的可信来源。 */
    private final AiMessageOriginPort origins;
    /** 所选AI同源短事务边界。 */
    private final AiTransactionExecutor transactions;

    /**
     * 创建群聊会话和成员，不发送模型请求。
     * @param title 可选标题，最多30字符；省略时根据真实成员名称生成
     * @param memberCodes 已选择的有序成员编码，目录将重新校验
     * @param expectedContext 宿主权限检查后的归属快照，不作为认证凭据
     * @return 真实数据库生成的会话编号
     */
    public Long createConversationForContext(String title, List<String> memberCodes, AiInvocationContext expectedContext) {
        return transactions.required(() -> {
            AiInvocationContext context = capture(expectedContext);
            AiApplicationConfig config = applications.load(context, AiChatMode.GROUP);
            authorize(context, config);
            return preparation.create(context, title, memberCodes);
        });
    }

    /**
     * 在真实发送事务内完整准备本轮；方法返回后才由 MVC 执行模型流。
     * @param conversationId 当前用户群聊会话编号
     * @param content 本轮非空问题，最多10000字符
     * @param expectedContext 宿主权限检查后的完整身份归属
     * @return 事务提交后可执行的流式响应
     */
    public AiPreparedExecution sendMessageForContext(Long conversationId, String content, AiInvocationContext expectedContext) {
        return transactions.required(() -> {
            if (conversationId == null || conversationId <= 0 || !StringUtils.hasText(content) || content.length() > 10000) {
                throw new IllegalArgumentException("群聊请求无效");
            }
            AiInvocationContext context = capture(expectedContext);
            AiApplicationConfig config = applications.load(context, AiChatMode.GROUP);
            if (!client.isConfigured(AiChatMode.GROUP, config.getAppId())) { throw new AiExecutionException(BAILIAN_CONFIG_INVALID); }
            String credential = authorize(context, config);
            AiGroupChatPreparedTurn turn = preparation.prepare(context, conversationId, content.trim(), config.getAppId(),
                    policy.getStaleGenerationSeconds());
            origins.record(context, AiChatMode.GROUP, turn.getMessageId(), config.getAppId());
            String trace = "GROUP-" + turn.getConversationId() + "-" + turn.getMessageId();
            audit.start(context, AiChatMode.GROUP, turn.getConversationId(), turn.getMessageId(), trace, config.getAppId());
            return stream.createResponseForContext(context, turn.getConversationId(), turn.getSessionId(), turn.getMessageId(),
                    content.trim(), turn.getMembers(), turn.getHistory(), trace, config, credential, turn.isAppChanged());
        });
    }

    /**
     * 归属验证后的停止 CAS 与审计共用真实事务，成功才取消本实例模型连接。
     * @param messageId 助手占位编号
     * @param expectedContext 宿主停止权限检查后的完整身份归属
     */
    public void stopMessageForContext(Long messageId, AiInvocationContext expectedContext) {
        transactions.runRequired(() -> {
            transactions.requireActive();
            if (messageId == null || messageId <= 0) { throw new IllegalArgumentException("群聊消息编号无效"); }
            AiInvocationContext context = capture(expectedContext);
            AiSingleChatStopResult result = preparation.stop(context, messageId);
            if (result == AiSingleChatStopResult.NOT_FOUND) { throw new AiExecutionException(GROUP_MESSAGE_NOT_EXISTS); }
            if (result != AiSingleChatStopResult.STOPPED) { throw new AiExecutionException(GROUP_MESSAGE_NOT_GENERATING); }
            audit.stop(context, AiChatMode.GROUP, messageId, ERROR_CODE_USER_STOPPED);
            transactions.afterCommit(() -> client.cancel(AiChatMode.GROUP, messageId));
        });
    }

    /** 最后一次身份捕获之后先核对授权归属，禁止跨命名空间或租户复用授权。 */
    private AiInvocationContext capture(AiInvocationContext expected) {
        if (expected == null) { throw new IllegalArgumentException("群聊授权上下文缺失"); }
        return AiInvocationGuard.capture(identities, expected.getActorId(), expected);
    }

    /** 无工具也必须校验应用授权；有工具时缺少凭据不能继续调用模型。 */
    private String authorize(AiInvocationContext context, AiApplicationConfig config) {
        return AiInvocationGuard.authorize(authorization, context, config, AiChatMode.GROUP);
    }
}
