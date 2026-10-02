package io.github.yoyocw.aichatkit.ai.starter.host;

import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.service.groupchat.AiGroupChatExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MIN_MEMBER_COUNT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MAX_MEMBER_COUNT;

/**
 * 宿主群聊授权入口，仅供已认证宿主服务调用，不自动开放 HTTP。
 * 创建、发送、停止各自核验独立群聊权限；引擎仍负责成员目录、归属及事务准备。
 * 群聊工作流工具的 JWT 传递、具体工具授权不由本门面替代。
 */
public final class AiHostGroupChatService {
    /** 真实用户认证及固定群聊权限边界。 */
    private final AiHostAuthenticationBridge authenticationBridge;
    /** 由容器管理并绑定真实事务的群聊同步入口，不能使用仅流式执行的组件替代。 */
    private final AiGroupChatExecutor executionService;

    /**
     * @param authenticationBridge 真实宿主认证桥接
     * @param executionService 容器管理的完整群聊同步服务
     * @throws NullPointerException 任一必要服务缺失
     */
    public AiHostGroupChatService(AiHostAuthenticationBridge authenticationBridge,
                                 AiGroupChatExecutor executionService) {
        this.authenticationBridge = Objects.requireNonNull(authenticationBridge, "宿主认证桥接不能为空");
        this.executionService = Objects.requireNonNull(executionService, "群聊引擎不能为空");
    }

    /**
     * 授权创建群聊，成员存在性、可用性、数量等业务约束由引擎及真实目录校验。
     * @param title 会话标题
     * @param memberCodes 有序成员编码，不授予访问对应成员的权限
     * @param expectedActorId 仅用于与真实登录身份匹配的预期用户标识
     * @return 事务创建的群聊会话编号
     * @throws IllegalArgumentException 成员列表缺失或引擎业务参数校验失败
     * @throws IllegalStateException 宿主认证失败或授权与执行身份不一致
     */
    public Long create(String title, List<String> memberCodes, String expectedActorId) {
        if (memberCodes == null || memberCodes.size() < MIN_MEMBER_COUNT || memberCodes.size() > MAX_MEMBER_COUNT) {
            throw new IllegalArgumentException("群聊成员数量必须为" + MIN_MEMBER_COUNT + "至" + MAX_MEMBER_COUNT + "个");
        }
        // 在远程权限查询前固定本次参数，后续不读取调用方可变列表。
        List<String> members = new ArrayList<>(memberCodes);
        AiHostSession session = authenticationBridge.authorizeCurrent(expectedActorId, AiHostAction.GROUP_CHAT_CREATE);
        return executionService.createConversationForContext(title, members, expectedContext(session));
    }

    /**
     * 授权群聊发送并调用事务准备，成员权限、来源及审计仍由引擎处理。
     * @param conversationId 正数群聊会话编号
     * @param content 本轮问题，由引擎校验内容与长度
     * @param expectedActorId 仅用于与真实登录身份匹配的预期用户标识
     * @return 事务提交后可消费的中立执行
     * @throws IllegalArgumentException 会话编号或内容无效
     * @throws IllegalStateException 宿主认证失败或授权与执行身份不一致
     */
    public AiPreparedExecution send(Long conversationId, String content, String expectedActorId) {
        return prepare(conversationId, content, expectedActorId);
    }

    /** Authorize and prepare synchronously; consume only after the actual transaction commit. */
    public AiPreparedExecution prepare(Long conversationId, String content, String expectedActorId) {
        requireId(conversationId);
        AiHostSession session = authenticationBridge.authorizeCurrent(expectedActorId, AiHostAction.GROUP_CHAT_SEND);
        return executionService.sendMessageForContext(conversationId, content, expectedContext(session));
    }

    /**
     * 独立核验群聊停止权限，消息归属和生成状态仍由引擎核验。
     * @param messageId 正数群聊助手消息编号
     * @param expectedActorId 仅用于与真实登录身份匹配的预期用户标识
     * @throws IllegalArgumentException 消息编号无效
     * @throws IllegalStateException 宿主认证失败或授权与执行身份不一致
     */
    public void stop(Long messageId, String expectedActorId) {
        requireId(messageId);
        AiHostSession session = authenticationBridge.authorizeCurrent(expectedActorId, AiHostAction.GROUP_CHAT_STOP);
        executionService.stopMessageForContext(messageId, expectedContext(session));
    }

    /** 引擎将重新捕获真实身份，三元匹配成功后才读取配置或操作存储。 */
    private AiInvocationContext expectedContext(AiHostSession session) {
        if (session.getExpiresAtMillis() <= System.currentTimeMillis()) {
            throw new AiIdentityException(AiIdentityError.UNAUTHENTICATED);
        }
        return new AiInvocationContext(session.getNamespace(), session.getTenantId(), session.getActorId(),
                UUID.randomUUID().toString());
    }

    /** 阻止空或非正数编号进入宿主授权与业务调用链。 */
    private void requireId(Long id) {
        if (id == null || id <= 0) { throw new IllegalArgumentException("群聊记录编号无效"); }
    }
}
