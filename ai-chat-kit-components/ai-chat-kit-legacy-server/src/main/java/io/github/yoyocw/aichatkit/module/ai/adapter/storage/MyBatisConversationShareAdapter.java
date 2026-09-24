package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.share.*;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.*;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.*;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.*;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.*;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiShareConstants.*;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.CHAT_CONVERSATION_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_CONVERSATION_NOT_EXISTS;

/** 原单群聊表的分享端口；只参与选中事务，公开能力不创建登录身份或切换存储。 */
@Component
@RequiredArgsConstructor
public class MyBatisConversationShareAdapter implements AiConversationSharePort {
    /** 与原MyBatis实际资源一致的事务能力。 */
    private final AiTransactionExecutor transactions;
    /** 原单聊父记录及分享状态。 */
    private final AiChatConversationMapper singles;
    /** 原单聊已完成消息。 */
    private final AiChatMessageMapper singleMessages;
    /** 原群聊父记录及分享状态。 */
    private final AiGroupChatConversationMapper groups;
    /** 原群聊已完成消息。 */
    private final AiGroupChatMessageMapper groupMessages;
    /** 原群聊有序成员关系。 */
    private final AiGroupChatMemberMapper groupMembers;
    /** 保留原目录名称、职责与缺失成员错误。 */
    private final MyBatisGroupChatSupport groupSupport;

    /** 锁父会话后复用或创建；唯一冲突直接传播，由外层按原事务定义重试。 */
    @Override
    public AiShareLease issue(AiInvocationContext context, AiChatMode mode, Long id, int days, String code) {
        transactions.requireActive();
        long userId = owner(context);
        mode(mode);
        if (days < MIN_VALID_DAYS || days > MAX_VALID_DAYS) { throw new AiExecutionException(SHARE_VALID_DAYS_INVALID); }
        if (code == null || !code.matches("[0-9a-f]{32}")) { throw new IllegalArgumentException("分享候选码无效"); }
        return mode == AiChatMode.SINGLE ? issueSingle(id, userId, days, code) : issueGroup(id, userId, days, code);
    }

    /** 单聊先锁归属父行；本轮重新启用时由原Mapper重置统计。 */
    private AiShareLease issueSingle(Long id, long userId, int days, String code) {
        AiChatConversationDO row = singles.selectByIdAndUserIdForUpdate(id, userId);
        if (row == null) { throw exception(CHAT_CONVERSATION_NOT_EXISTS); }
        LocalDateTime now = LocalDateTime.now();
        if (active(row.getShareStatus(), row.getShareCode(), row.getShareExpireTime(), now)) {
            return lease(row.getShareCode(), row.getShareExpireTime());
        }
        LocalDateTime expires = now.plusDays(days);
        singles.activateShare(id, userId, code, expires);
        return lease(code, expires);
    }

    /** 群聊只改变原父行分享字段，不读取新成员快照表。 */
    private AiShareLease issueGroup(Long id, long userId, int days, String code) {
        AiGroupChatConversationDO row = groups.selectByIdAndUserIdForUpdate(id, userId);
        if (row == null) { throw exception(GROUP_CONVERSATION_NOT_EXISTS); }
        LocalDateTime now = LocalDateTime.now();
        if (active(row.getShareStatus(), row.getShareCode(), row.getShareExpireTime(), now)) {
            return lease(row.getShareCode(), row.getShareExpireTime());
        }
        LocalDateTime expires = now.plusDays(days);
        groups.activateShare(id, userId, code, expires);
        return lease(code, expires);
    }

    /** 按原父行锁顺序撤销，访问统计由原Mapper保留。 */
    @Override
    public void revoke(AiInvocationContext context, AiChatMode mode, Long id) {
        transactions.requireActive();
        long userId = owner(context);
        mode(mode);
        if (mode == AiChatMode.SINGLE) {
            if (singles.selectByIdAndUserIdForUpdate(id, userId) == null) {
                throw exception(CHAT_CONVERSATION_NOT_EXISTS);
            }
            singles.disableShare(id, userId);
        } else {
            if (groups.selectByIdAndUserIdForUpdate(id, userId) == null) {
                throw exception(GROUP_CONVERSATION_NOT_EXISTS);
            }
            groups.disableShare(id, userId);
        }
    }

    /** 仅公开码读取忽略租户；命中父行决定子记录归属，最终条件计数失败不返回内容。 */
    @Override
    @TenantIgnore
    public AiSharedConversation readPublic(AiChatMode mode, String code) {
        transactions.requireActive();
        mode(mode);
        if (code == null || !code.matches("[0-9a-f]{32}")) { throw missing(mode); }
        return mode == AiChatMode.SINGLE ? readSingle(code) : readGroup(code);
    }

    /** 不增加父行预锁，保留读消息后再条件更新计数的原并发顺序。 */
    private AiSharedConversation readSingle(String code) {
        AiChatConversationDO row = singles.selectActiveShare(code, LocalDateTime.now());
        if (row == null) { throw missing(AiChatMode.SINGLE); }
        List<AiSharedMessage> messages = new ArrayList<>();
        for (AiChatMessageDO message : singleMessages.selectCompletedListByConversationId(row.getId())) {
            checkOwner(row.getUserId(), message.getUserId(), AiChatMode.SINGLE);
            messages.add(message(message.getRole(), message.getContent(), null, null, null, message.getCreateTime()));
        }
        AiSharedConversation result = new AiSharedConversation(row.getTitle(), Collections.emptyList(), messages);
        if (singles.incrementShareAccess(row.getId(), code, LocalDateTime.now()) == 0) { throw missing(AiChatMode.SINGLE); }
        return result;
    }

    /** 原群成员从真实目录按关系顺序还原；不要求启用，不改成新库历史快照语义。 */
    private AiSharedConversation readGroup(String code) {
        AiGroupChatConversationDO row = groups.selectActiveShare(code, LocalDateTime.now());
        if (row == null) { throw missing(AiChatMode.GROUP); }
        List<String> codes = new ArrayList<>();
        for (AiGroupChatMemberDO member : groupMembers.selectListByConversationId(row.getId())) {
            checkOwner(row.getUserId(), member.getUserId(), AiChatMode.GROUP);
            codes.add(member.getAgentCode());
        }
        List<AiGroupMemberSnapshot> members = groupSupport.toAgents(codes);
        List<AiSharedMessage> messages = new ArrayList<>();
        for (AiGroupChatMessageDO message : groupMessages.selectCompletedListByConversationId(row.getId())) {
            checkOwner(row.getUserId(), message.getUserId(), AiChatMode.GROUP);
            messages.add(message(message.getRole(), message.getContent(), message.getSpeakerCode(),
                    message.getSpeakerName(), message.getRoundNo(), message.getCreateTime()));
        }
        AiSharedConversation result = new AiSharedConversation(row.getTitle(), codes, messages, members);
        if (groups.incrementShareAccess(row.getId(), code, LocalDateTime.now()) == 0) { throw missing(AiChatMode.GROUP); }
        return result;
    }

    /** 一次读取同时携带旧墙上时间及中立毫秒；不公开DO内部字段。 */
    private AiSharedMessage message(String role, String content, String speakerCode, String speakerName,
            Integer roundNo, LocalDateTime created) {
        if (created == null) { throw new IllegalStateException("原分享消息创建时间缺失"); }
        return new AiSharedMessage(role, content, speakerCode, speakerName, roundNo, millis(created), created);
    }

    /** 参数仅匹配已经存在的宿主租户作用域，不能开启忽略或按参数切换租户。 */
    private long owner(AiInvocationContext context) {
        if (context == null || !"platform".equals(context.getNamespace()) || !StringUtils.hasText(context.getInvocationId())
                || TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId() == null
                || !TenantContextHolder.getTenantId().toString().equals(context.getTenantId())
                || context.getActorId() == null || !context.getActorId().matches("[1-9][0-9]{0,18}")) {
            throw new IllegalStateException("分享宿主身份不匹配");
        }
        try { return Long.parseLong(context.getActorId()); }
        catch (NumberFormatException ex) { throw new IllegalStateException("分享宿主身份不匹配"); }
    }

    /** 子记录归属必须来自已命中的父记录，不接受访客提供用户编号。 */
    private void checkOwner(Long parentUser, Long childUser, AiChatMode mode) {
        if (parentUser == null || !Objects.equals(parentUser, childUser)) { throw missing(mode); }
    }
    /** 保留原有效分享复用规则，不重置期限或统计。 */
    private boolean active(Integer status, String code, LocalDateTime expires, LocalDateTime now) {
        return Objects.equals(status, STATUS_ACTIVE) && StringUtils.hasText(code) && expires != null && expires.isAfter(now);
    }
    /** 到期时间仅转换一次；有效期判断仍使用原LocalDateTime。 */
    private AiShareLease lease(String code, LocalDateTime expires) { return new AiShareLease(code, millis(expires)); }
    /** 保留旧宿主时间戳序列化时区。 */
    private long millis(LocalDateTime value) { return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    /** 非单群模式不得选择任意表。 */
    private void mode(AiChatMode mode) {
        if (mode != AiChatMode.SINGLE && mode != AiChatMode.GROUP) { throw new IllegalArgumentException("分享模式无效"); }
    }
    /** 公开错误不区分撤销、过期或其他归属。 */
    private AiExecutionException missing(AiChatMode mode) {
        return new AiExecutionException(mode == AiChatMode.SINGLE ? CHAT_SHARE_NOT_EXISTS : GROUP_SHARE_NOT_EXISTS);
    }
}
