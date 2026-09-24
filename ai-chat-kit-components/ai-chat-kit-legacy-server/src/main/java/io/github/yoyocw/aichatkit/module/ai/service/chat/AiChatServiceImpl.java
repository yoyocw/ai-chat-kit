package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.adapter.platform.share.PlatformConversationShareBridge;
import io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.*;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.management.PlatformConversationManagementBridge;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.*;


/**
 * AI 智能对话服务实现，负责本地会话持久化并将模型能力统一委托给百炼应用。
 */
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    /** 单聊执行服务，统一处理发送准备、百炼调用、消息终态与停止生成。 */
    private final AiChatExecutionService executionService;
    /** 分享统一走真实原表适配与旧响应桥接。 */
    private final PlatformConversationShareBridge shareBridge;
    /** 管理用例统一走原表端口，并保留旧响应字段。 */
    private final PlatformConversationManagementBridge managementBridge;

    @Override
    public Long createConversation(Long userId) {
        return managementBridge.createSingle(userId);
    }

    @Override
    public void updateConversation(AiChatConversationUpdateReqVO reqVO, Long userId) {
        managementBridge.rename(AiChatMode.SINGLE, reqVO.getId(), reqVO.getTitle(), userId);
    }

    @Override
    public void pinConversation(AiChatConversationPinReqVO reqVO, Long userId) {
        managementBridge.pin(AiChatMode.SINGLE, reqVO, userId);
    }

    @Override
    public AiChatConversationShareRespVO shareConversation(AiChatConversationShareReqVO reqVO, Long userId) {
        return shareBridge.issueSingle(reqVO, userId);
    }

    @Override
    public void cancelConversationShare(Long id, Long userId) {
        shareBridge.revokeSingle(id, userId);
    }

    @Override
    @TenantIgnore
    public AiChatShareRespVO getSharedConversation(String shareCode) {
        return shareBridge.readSingle(shareCode);
    }

    @Override
    public void deleteConversation(Long id, Long userId) {
        managementBridge.delete(AiChatMode.SINGLE, id, userId);
    }

    @Override
    public List<AiChatConversationRespVO> getConversationList(Long userId) {
        return managementBridge.listSingle(userId);
    }

    @Override
    public List<AiChatMessageRespVO> getMessageList(Long conversationId, Long userId) {
        return managementBridge.messagesSingle(conversationId, userId);
    }

    @Override
    public StreamingResponseBody sendMessage(AiChatSendReqVO reqVO, Long userId) {
        try {
            return executionService.sendMessage(reqVO, userId);
        } catch (AiExecutionException ex) {
            // 保留宿主响应格式，引擎不依赖林业公共异常类型。
            throw new ServiceException(ex.getError().getCode(), ex.getError().getMsg());
        }
    }

    @Override
    public void stopMessage(Long messageId, Long userId) {
        try {
            executionService.stopMessage(messageId, userId);
        } catch (AiExecutionException ex) {
            // 保留消息不存在和非生成状态的既有业务码。
            throw new ServiceException(ex.getError().getCode(), ex.getError().getMsg());
        }
    }

}
