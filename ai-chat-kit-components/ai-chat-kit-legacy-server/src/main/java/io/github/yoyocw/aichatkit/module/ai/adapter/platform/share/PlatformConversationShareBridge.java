package io.github.yoyocw.aichatkit.module.ai.adapter.platform.share;

import io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.share.*;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.*;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.*;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 原分享接口桥接；权限仍由原Controller检查，固定模式且只返回原公开VO字段。 */
@Component
@RequiredArgsConstructor
public class PlatformConversationShareBridge {
    /** 使用原表及宿主事务定义的分享用例。 */
    private final AiConversationShareService shares;
    /** 从真实宿主上下文核对调用者，不能从请求伪造归属。 */
    private final AiInvocationContextPort identities;

    /** @param request 原单聊分享请求 @param userId 已授权用户 @return 原分享响应 */
    public AiChatConversationShareRespVO issueSingle(AiChatConversationShareReqVO request, Long userId) {
        return issue(AiChatMode.SINGLE, request, userId);
    }
    /** @param request 原群聊分享请求 @param userId 已授权用户 @return 原分享响应 */
    public AiChatConversationShareRespVO issueGroup(AiChatConversationShareReqVO request, Long userId) {
        return issue(AiChatMode.GROUP, request, userId);
    }
    /** @param id 已授权单聊编号 @param userId 当前用户；沿用原幂等撤销 */
    public void revokeSingle(Long id, Long userId) { revoke(AiChatMode.SINGLE, id, userId); }
    /** @param id 已授权群聊编号 @param userId 当前用户；沿用原幂等撤销 */
    public void revokeGroup(Long id, Long userId) { revoke(AiChatMode.GROUP, id, userId); }

    /** @param code 唯一公开能力参数 @return 标题和已完成单聊消息，不建立访客身份 */
    @TenantIgnore
    public AiChatShareRespVO readSingle(String code) {
        return mapped(() -> {
            AiSharedConversation value = shares.readPublic(AiChatMode.SINGLE, code);
            List<AiChatShareMessageRespVO> messages = new ArrayList<>();
            for (AiSharedMessage message : value.getMessages()) {
                AiChatShareMessageRespVO item = new AiChatShareMessageRespVO();
                item.setRole(message.getRole());
                item.setContent(message.getContent());
                item.setCreateTime(sourceTime(message));
                messages.add(item);
            }
            AiChatShareRespVO result = new AiChatShareRespVO();
            result.setTitle(value.getTitle());
            result.setMessages(messages);
            return result;
        });
    }

    /** @param code 唯一公开能力参数 @return 原目录成员和已完成群聊消息安全字段 */
    @TenantIgnore
    public AiGroupChatShareRespVO readGroup(String code) {
        return mapped(() -> {
            AiSharedConversation value = shares.readPublic(AiChatMode.GROUP, code);
            List<AiGroupChatShareMessageRespVO> messages = new ArrayList<>();
            for (AiSharedMessage message : value.getMessages()) {
                AiGroupChatShareMessageRespVO item = new AiGroupChatShareMessageRespVO();
                item.setRole(message.getRole());
                item.setContent(message.getContent());
                item.setSpeakerCode(message.getSpeakerCode());
                item.setSpeakerName(message.getSpeakerName());
                item.setRoundNo(message.getRoundNo());
                item.setCreateTime(sourceTime(message));
                messages.add(item);
            }
            AiGroupChatShareRespVO result = new AiGroupChatShareRespVO();
            result.setTitle(value.getTitle());
            result.setMessages(messages);
            result.setMembers(members(value));
            return result;
        });
    }

    /** 只复制公开目录的三个字段，不使用整个宿主实体序列化。 */
    private List<AiGroupChatAgentRespVO> members(AiSharedConversation value) {
        List<AiGroupChatAgentRespVO> result = new ArrayList<>();
        for (AiGroupMemberSnapshot member : value.getMembers()) {
            AiGroupChatAgentRespVO item = new AiGroupChatAgentRespVO();
            item.setCode(member.getCode());
            item.setName(member.getName());
            item.setRole(member.getRole());
            result.add(item);
        }
        return result;
    }

    /** 使用同次查询的原时间，不把毫秒反构造为丢失精度的旧VO。 */
    private java.time.LocalDateTime sourceTime(AiSharedMessage value) {
        if (value.getSourceCreateTime() == null) { throw new IllegalStateException("旧分享响应缺少原存储时间"); }
        return value.getSourceCreateTime();
    }

    /** 创建只捕获原授权用户，URL及毫秒期限由分享用例提供。 */
    private AiChatConversationShareRespVO issue(AiChatMode mode, AiChatConversationShareReqVO request, Long userId) {
        return mapped(() -> {
            AiShareGrant grant = shares.issueForContext(mode, request.getId(), request.getValidDays(),
                    identities.capture(userId == null ? null : userId.toString()));
            AiChatConversationShareRespVO result = new AiChatConversationShareRespVO();
            result.setShareCode(grant.getShareCode());
            result.setShareUrl(grant.getShareUrl());
            result.setExpireTime(grant.getExpireTimeMillis());
            return result;
        });
    }

    /** 撤销沿用原权限和归属，不加入聊天发送或工具授权。 */
    private void revoke(AiChatMode mode, Long id, Long userId) {
        mapped(() -> {
            shares.revokeForContext(mode, id, identities.capture(userId == null ? null : userId.toString()));
            return null;
        });
    }

    /** 中立业务错误映射回旧宿主错误码；其他异常保持原传播方式。 */
    private <T> T mapped(Supplier<T> action) {
        try { return action.get(); }
        catch (AiExecutionException ex) { throw new ServiceException(ex.getError().getCode(), ex.getError().getMsg()); }
    }
}
