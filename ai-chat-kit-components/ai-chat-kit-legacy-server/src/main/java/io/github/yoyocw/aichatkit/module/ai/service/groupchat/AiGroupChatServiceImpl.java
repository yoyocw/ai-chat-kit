package io.github.yoyocw.aichatkit.module.ai.service.groupchat;

import io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiGroupChatPreparePort;
import io.github.yoyocw.aichatkit.module.ai.adapter.storage.MyBatisGroupChatSupport;

import io.github.yoyocw.aichatkit.module.ai.adapter.platform.share.PlatformConversationShareBridge;
import io.github.yoyocw.aichatkit.compat.framework.common.util.object.BeanUtils;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationPinReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatAgentRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationCreateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatMemberUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatMessageRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatShareRespVO;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.management.PlatformConversationManagementBridge;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.ArrayList;
import java.util.List;

import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_CONVERSATION_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_MESSAGE_NOT_EXISTS;

/**
 * AI 群聊服务实现，保存本地会话事实并调用单个百炼工作流完成多智能体编排。
 */
@Service
@RequiredArgsConstructor
public class AiGroupChatServiceImpl implements AiGroupChatService {

    /** 群聊同步宿主身份桥接，先于任何消息写入。 */
    private final AiInvocationContextPort contextPort;
    /** 旧群聊写入使用已核验的林业同源事务。 */
    private final AiTransactionExecutor transactions;
    /** 原接口实际委托的群聊发送及停止引擎。 */
    private final AiGroupChatExecutionService executionService;
    /** 分享统一走真实原表适配与旧响应桥接。 */
    private final PlatformConversationShareBridge shareBridge;
    /** 管理用例统一走原表端口，并保留旧响应字段。 */
    private final PlatformConversationManagementBridge managementBridge;
    /** 原表创建端口；旧创建不增加引擎的应用授权。 */
    private final AiGroupChatPreparePort preparation;
    /** 原表成员、目录及锁操作的单一实现。 */
    private final MyBatisGroupChatSupport support;

    @Override
    public List<AiGroupChatAgentRespVO> getAgentList() {
        return BeanUtils.toBean(support.getAgents(), AiGroupChatAgentRespVO.class);
    }

    @Override
    public Long createConversation(AiGroupChatConversationCreateReqVO reqVO, Long userId) {
        return transactions.required(() -> preparation.create(
                contextPort.capture(userId == null ? null : userId.toString()), reqVO.getTitle(),
                reqVO.getMemberCodes() == null ? null : new ArrayList<>(reqVO.getMemberCodes())));
    }

    @Override
    public void updateConversation(AiGroupChatConversationUpdateReqVO reqVO, Long userId) {
        managementBridge.rename(AiChatMode.GROUP, reqVO.getId(), reqVO.getTitle(), userId);
    }

    @Override
    public void pinConversation(AiChatConversationPinReqVO reqVO, Long userId) {
        managementBridge.pin(AiChatMode.GROUP, reqVO, userId);
    }

    @Override
    public AiChatConversationShareRespVO shareConversation(AiChatConversationShareReqVO reqVO, Long userId) {
        return shareBridge.issueGroup(reqVO, userId);
    }

    @Override
    public void cancelConversationShare(Long id, Long userId) {
        shareBridge.revokeGroup(id, userId);
    }

    @Override
    @TenantIgnore
    public AiGroupChatShareRespVO getSharedConversation(String shareCode) {
        return shareBridge.readGroup(shareCode);
    }

    @Override
    public void updateMembers(AiGroupChatMemberUpdateReqVO reqVO, Long userId) {
        managementBridge.updateMembers(reqVO, userId);
    }

    @Override
    public void deleteConversation(Long id, Long userId) {
        managementBridge.delete(AiChatMode.GROUP, id, userId);
    }

    @Override
    public List<AiGroupChatConversationRespVO> getConversationList(Long userId) {
        return managementBridge.listGroup(userId);
    }

    @Override
    public List<AiGroupChatMessageRespVO> getMessageList(Long conversationId, Long userId) {
        return managementBridge.messagesGroup(conversationId, userId);
    }

    @Override
    public StreamingResponseBody sendMessage(AiGroupChatSendReqVO reqVO, Long userId) {
        try {
            // 原权限由 Controller 保留；引擎再次核对真实归属并执行本轮应用与工具授权。
            AiInvocationContext context = contextPort.capture(userId == null ? null : userId.toString());
            // 旧 Controller 仅校验非空，非正编号继续使用原查询未命中的业务码。
            if (reqVO.getConversationId() != null && reqVO.getConversationId() <= 0) {
                throw exception(GROUP_CONVERSATION_NOT_EXISTS);
            }
            return executionService.sendMessageForContext(reqVO.getConversationId(), reqVO.getContent(), context);
        } catch (AiExecutionException ex) {
            throw new ServiceException(ex.getError().getCode(), ex.getError().getMsg());
        }
    }

    @Override
    public void stopMessage(Long messageId, Long userId) {
        try {
            AiInvocationContext context = contextPort.capture(userId == null ? null : userId.toString());
            // 保留旧接口对非正消息编号的错误分类，引擎仍执行严格参数校验。
            if (messageId != null && messageId <= 0) {
                throw exception(GROUP_MESSAGE_NOT_EXISTS);
            }
            executionService.stopMessageForContext(messageId, context);
        } catch (AiExecutionException ex) {
            // 保留原消息不存在和非生成状态的业务码，不改变 HTTP 响应结构。
            throw new ServiceException(ex.getError().getCode(), ex.getError().getMsg());
        }
    }

}
