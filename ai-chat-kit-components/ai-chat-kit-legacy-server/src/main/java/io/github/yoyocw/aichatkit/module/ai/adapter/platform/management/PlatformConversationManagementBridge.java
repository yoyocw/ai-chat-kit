package io.github.yoyocw.aichatkit.module.ai.adapter.platform.management;

import io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException;
import io.github.yoyocw.aichatkit.compat.framework.common.util.object.BeanUtils;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.*;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.*;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 原林业管理接口桥接；身份由宿主捕获，旧响应使用原始字段，不反向调用旧 Service。 */
@Component
@RequiredArgsConstructor
public class PlatformConversationManagementBridge {
    /** 中立管理用例负责事务及提交后的模型取消。 */
    private final AiConversationManagementService management;
    /** 捕获当前登录用户，不接受客户端自造上下文。 */
    private final AiInvocationContextPort identities;

    /** 创建空单聊，不增加发送或模型应用权限。 */
    public Long createSingle(Long userId) {
        return invoke(() -> management.createSingleForContext(context(userId)));
    }

    /** 在宿主调用者指定的固定模式内重命名；保留原业务错误码。 */
    public void rename(AiChatMode mode, Long id, String title, Long userId) {
        invoke(() -> { management.renameForContext(mode, id, title, context(userId)); return null; });
    }

    /** 显式置顶意图不可为空，取消置顶由原表适配器清空时间。 */
    public void pin(AiChatMode mode, AiChatConversationPinReqVO request, Long userId) {
        if (request.getPinned() == null) { throw new IllegalArgumentException("置顶状态不能为空"); }
        invoke(() -> { management.pinForContext(mode, request.getId(), request.getPinned(), context(userId)); return null; });
    }

    /** 删除只在事务提交后取消本模式的生成任务。 */
    public void delete(AiChatMode mode, Long id, Long userId) {
        invoke(() -> { management.deleteForContext(mode, id, context(userId)); return null; });
    }

    /** 群聊成员保持原输入顺序，由原表适配器规范化并检查生成状态。 */
    public void updateMembers(AiGroupChatMemberUpdateReqVO request, Long userId) {
        invoke(() -> {
            management.updateGroupMembersForContext(request.getId(), request.getMemberCodes() == null
                    ? null : new ArrayList<>(request.getMemberCodes()), context(userId));
            return null;
        });
    }

    /** 返回原单聊列表排序、可空置顶值及原始时间。 */
    public List<AiChatConversationRespVO> listSingle(Long userId) {
        return invoke(() -> {
            List<AiChatConversationRespVO> result = new ArrayList<>();
            for (AiConversationView row : management.listConversationsForContext(AiChatMode.SINGLE, context(userId))) {
                requireSource(row.hasSourceValues());
                AiChatConversationRespVO item = new AiChatConversationRespVO();
                item.setId(row.getId()); item.setTitle(row.getTitle()); item.setPinned(row.getSourcePinned());
                item.setPinnedTime(row.getSourcePinnedTime()); item.setUpdateTime(row.getSourceUpdateTime());
                result.add(item);
            }
            return result;
        });
    }

    /** 原群聊成员来自当前目录，不能误标记为创建时快照。 */
    public List<AiGroupChatConversationRespVO> listGroup(Long userId) {
        return invoke(() -> {
            List<AiGroupChatConversationRespVO> result = new ArrayList<>();
            for (AiConversationView row : management.listConversationsForContext(AiChatMode.GROUP, context(userId))) {
                requireSource(row.hasSourceValues());
                AiGroupChatConversationRespVO item = new AiGroupChatConversationRespVO();
                item.setId(row.getId()); item.setTitle(row.getTitle()); item.setPinned(row.getSourcePinned());
                item.setPinnedTime(row.getSourcePinnedTime()); item.setUpdateTime(row.getSourceUpdateTime());
                item.setCreateTime(row.getSourceCreateTime());
                item.setMembers(BeanUtils.toBean(row.getMembers(), AiGroupChatAgentRespVO.class));
                result.add(item);
            }
            return result;
        });
    }

    /** 单聊消息保留原可空状态和地图值；禁止通过 primitive getter 补默认值。 */
    public List<AiChatMessageRespVO> messagesSingle(Long id, Long userId) {
        return invoke(() -> {
            List<AiChatMessageRespVO> result = new ArrayList<>();
            for (AiMessageView row : management.listMessagesForContext(AiChatMode.SINGLE, id, context(userId))) {
                requireSource(row.hasSourceValues());
                AiChatMessageRespVO item = new AiChatMessageRespVO();
                item.setId(row.getId()); item.setConversationId(row.getConversationId());
                item.setRole(row.getRole()); item.setContent(row.getContent());
                item.setMapEnabled(row.getSourceMapEnabled()); item.setStatus(row.getSourceStatus());
                item.setRequestId(row.getRequestId()); item.setResponseData(row.getResponseData());
                item.setErrorMessage(row.getErrorMessage()); item.setCreateTime(row.getSourceCreateTime());
                result.add(item);
            }
            return result;
        });
    }

    /** 群聊历史保持全部消息、原始时间精度及发言者字段。 */
    public List<AiGroupChatMessageRespVO> messagesGroup(Long id, Long userId) {
        return invoke(() -> {
            List<AiGroupChatMessageRespVO> result = new ArrayList<>();
            for (AiMessageView row : management.listMessagesForContext(AiChatMode.GROUP, id, context(userId))) {
                requireSource(row.hasSourceValues());
                AiGroupChatMessageRespVO item = new AiGroupChatMessageRespVO();
                item.setId(row.getId()); item.setConversationId(row.getConversationId());
                item.setRole(row.getRole()); item.setContent(row.getContent()); item.setStatus(row.getSourceStatus());
                item.setRequestId(row.getRequestId()); item.setResponseData(row.getResponseData());
                item.setErrorMessage(row.getErrorMessage()); item.setCreateTime(row.getSourceCreateTime());
                item.setSpeakerCode(row.getSpeakerCode()); item.setSpeakerName(row.getSpeakerName());
                item.setRoundNo(row.getRoundNo()); result.add(item);
            }
            return result;
        });
    }

    /** 允许身份端口按既有约定处理空用户，不制造匿名或默认用户。 */
    private AiInvocationContext context(Long userId) {
        return identities.capture(userId == null ? null : userId.toString());
    }

    /** 本桥接限定原表；不能将毫秒精度投影冒充原始宿主记录。 */
    private void requireSource(boolean present) {
        if (!present) { throw new IllegalStateException("原表管理响应缺少原始字段"); }
    }

    /** 引擎业务错误转换为旧 HTTP 响应，其他异常原样传播触发回滚。 */
    private <T> T invoke(Supplier<T> action) {
        try { return action.get(); }
        catch (AiExecutionException ex) { throw new ServiceException(ex.getError().getCode(), ex.getError().getMsg()); }
    }
}
