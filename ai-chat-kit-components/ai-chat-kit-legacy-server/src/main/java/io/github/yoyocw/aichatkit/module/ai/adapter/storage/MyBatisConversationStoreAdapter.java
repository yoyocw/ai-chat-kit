package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationStorePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMemberSnapshotStatus;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatConversationDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatConversationDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.*;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiModelExecutionConstants.ERROR_CODE_USER_STOPPED;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.CHAT_CONVERSATION_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_CONVERSATION_NOT_EXISTS;

/** 完整单群聊管理原表适配，不调用旧 Service，不访问 ai_runtime 或改变模型授权。 */
@Service
@RequiredArgsConstructor
public class MyBatisConversationStoreAdapter implements AiConversationStorePort {
    /** 与发送、完成和来源一致的事务资源。 */
    private final AiTransactionExecutor transactions;
    /** 原单聊会话表。 */
    private final AiChatConversationMapper singles;
    /** 原单聊消息表。 */
    private final AiChatMessageMapper singleMessages;
    /** 原群聊会话表。 */
    private final AiGroupChatConversationMapper groups;
    /** 原群聊消息表。 */
    private final AiGroupChatMessageMapper groupMessages;
    /** 原群聊成员关系。 */
    private final AiGroupChatMemberMapper members;
    /** 复用原目录排序、成员规范化及会话锁。 */
    private final MyBatisGroupChatSupport groupSupport;
    /** 删除时严格收口已停止的生成审计，失败不得吞掉。 */
    private final AiExecutionAuditPort audit;

    /** 创建默认标题空单聊，无模型配置或工具签发依赖。 */
    @Override
    public Long createSingle(AiInvocationContext context) {
        return transactions.mandatory(() -> {
            AiChatConversationDO row = AiChatConversationDO.builder().userId(user(context)).title(DEFAULT_TITLE).build();
            singles.insert(row);
            return row.getId();
        });
    }

    /** 保留原查询排序与当前目录投影；原始可空字段不补默认值。 */
    @Override
    public List<AiConversationView> list(AiInvocationContext context, AiChatMode mode) {
        Long owner = user(context); requireMode(mode);
        List<AiConversationView> result = new ArrayList<>();
        if (mode == AiChatMode.SINGLE) {
            for (AiChatConversationDO row : singles.selectListByUserId(owner)) {
                result.add(AiConversationView.fromSource(row.getId(), row.getTitle(), row.getPinned(), row.getPinnedTime(),
                        row.getUpdateTime(), Collections.emptyList(), row.getCreateTime(), Collections.emptyList(),
                        AiMemberSnapshotStatus.NOT_APPLICABLE));
            }
        } else {
            for (AiGroupChatConversationDO row : groups.selectListByUserId(owner)) {
                List<String> codes = groupSupport.loadMemberCodes(row.getId(), owner);
                result.add(AiConversationView.fromSource(row.getId(), row.getTitle(), row.getPinned(), row.getPinnedTime(),
                        row.getUpdateTime(), codes, row.getCreateTime(), groupSupport.toAgents(codes),
                        AiMemberSnapshotStatus.CURRENT_DIRECTORY));
            }
        }
        return result;
    }

    /** 先校验会话归属再按原消息 ID 升序全量读取，不截断历史。 */
    @Override
    public List<AiMessageView> messages(AiInvocationContext context, AiChatMode mode, Long id) {
        Long owner = user(context); requireMode(mode); owned(mode, id, owner, false);
        List<AiMessageView> result = new ArrayList<>();
        if (mode == AiChatMode.SINGLE) {
            for (AiChatMessageDO row : singleMessages.selectListByConversationId(id, owner)) {
                result.add(AiMessageView.fromSource(row.getId(), row.getConversationId(), row.getRole(), row.getContent(),
                        row.getMapEnabled(), row.getStatus(), row.getRequestId(), row.getResponseData(), row.getErrorMessage(),
                        null, null, null, row.getCreateTime()));
            }
        } else {
            for (AiGroupChatMessageDO row : groupMessages.selectListByConversationId(id, owner)) {
                result.add(AiMessageView.fromSource(row.getId(), row.getConversationId(), row.getRole(), row.getContent(),
                        null, row.getStatus(), row.getRequestId(), row.getResponseData(), row.getErrorMessage(),
                        row.getSpeakerCode(), row.getSpeakerName(), row.getRoundNo(), row.getCreateTime()));
            }
        }
        return result;
    }

    /** 会话锁内只改标题；生成中仍允许，原未命中业务错误先于内部标题兜底校验。 */
    @Override
    public void rename(AiInvocationContext context, AiChatMode mode, Long id, String title) {
        transactions.mandatory(() -> {
            Long owner = user(context); requireMode(mode); owned(mode, id, owner, true);
            if (!StringUtils.hasText(title) || title.length() > (mode == AiChatMode.SINGLE ? 100 : 30)) {
                throw new IllegalArgumentException("会话标题无效");
            }
            if (mode == AiChatMode.SINGLE) { singles.updateById(AiChatConversationDO.builder().id(id).title(title.trim()).build()); }
            else { groups.updateById(AiGroupChatConversationDO.builder().id(id).title(title.trim()).build()); }
            return null;
        });
    }

    /** 使用原服务器时间，取消置顶同时显式清空时间。 */
    @Override
    public void pin(AiInvocationContext context, AiChatMode mode, Long id, boolean pinned) {
        transactions.mandatory(() -> {
            Long owner = user(context); requireMode(mode); owned(mode, id, owner, true);
            LocalDateTime time = pinned ? LocalDateTime.now() : null;
            if (mode == AiChatMode.SINGLE) { singles.updatePin(id, owner, pinned, time); }
            else { groups.updatePin(id, owner, pinned, time); }
            return null;
        });
    }

    /** 会话锁串行化发送/完成，CAS 收口及审计失败全部回滚，返回成功停止的固定 ID。 */
    @Override
    public List<Long> delete(AiInvocationContext context, AiChatMode mode, Long id) {
        return transactions.mandatory(() -> {
            Long owner = user(context); requireMode(mode); owned(mode, id, owner, true);
            List<Long> stopped = mode == AiChatMode.SINGLE ? stopSingle(context, id, owner) : stopGroup(context, id, owner);
            // 保留原逻辑删除次序，来源和审计记录不删除；模型取消由引擎 afterCommit 执行。
            if (mode == AiChatMode.SINGLE) {
                singleMessages.delete(new LambdaQueryWrapperX<AiChatMessageDO>()
                        .eq(AiChatMessageDO::getConversationId, id).eq(AiChatMessageDO::getUserId, owner));
                singles.deleteById(id);
            } else {
                groupMessages.deleteByConversationId(id, owner);
                members.deleteByConversationId(id, owner);
                groups.deleteById(id);
            }
            return stopped;
        });
    }

    /** 锁内禁止生成中更新；沿用目录排序及成员原错误码，清云端 session/turn，保留本地历史和 app。 */
    @Override
    public void updateGroupMembers(AiInvocationContext context, Long id, List<String> codes) {
        transactions.mandatory(() -> {
            Long owner = user(context); owned(AiChatMode.GROUP, id, owner, true);
            groupSupport.ensureNotGenerating(id, owner);
            groupSupport.replaceMembers(id, owner, groupSupport.validateMembers(codes));
            groups.clearBailianSession(id, owner);
            groupSupport.touchConversation(id);
            return null;
        });
    }

    /** 只停止当前用户生成中的助手，CAS 失败不取消其它已终态调用。 */
    private List<Long> stopSingle(AiInvocationContext context, Long id, Long owner) {
        List<Long> result = new ArrayList<>();
        for (AiChatMessageDO row : singleMessages.selectListByConversationId(id, owner)) {
            if (ROLE_ASSISTANT.equals(row.getRole()) && Objects.equals(STATUS_GENERATING, row.getStatus())
                    && singleMessages.updateGeneratingMessage(AiChatMessageDO.builder().id(row.getId()).status(STATUS_STOPPED).build())) {
                audit.stop(context, AiChatMode.SINGLE, row.getId(), ERROR_CODE_USER_STOPPED);
                result.add(row.getId());
            }
        }
        return result;
    }

    /** 群聊取消命名空间独立，审计只针对 CAS 成功占位。 */
    private List<Long> stopGroup(AiInvocationContext context, Long id, Long owner) {
        List<Long> result = new ArrayList<>();
        for (AiGroupChatMessageDO row : groupMessages.selectListByConversationId(id, owner)) {
            if (ROLE_ASSISTANT.equals(row.getRole()) && Objects.equals(STATUS_GENERATING, row.getStatus())
                    && groupMessages.updateGeneratingMessage(AiGroupChatMessageDO.builder().id(row.getId()).status(STATUS_STOPPED).build())) {
                audit.stop(context, AiChatMode.GROUP, row.getId(), ERROR_CODE_USER_STOPPED);
                result.add(row.getId());
            }
        }
        return result;
    }

    /** 所有操作都限制原用户；非正 ID 沿用原“会话不存在”业务错误。 */
    private void owned(AiChatMode mode, Long id, Long owner, boolean lock) {
        if (mode == AiChatMode.SINGLE) {
            if (id == null || id <= 0 || (lock ? singles.selectByIdAndUserIdForUpdate(id, owner)
                    : singles.selectByIdAndUserId(id, owner)) == null) { throw exception(CHAT_CONVERSATION_NOT_EXISTS); }
        } else {
            if (id == null || id <= 0 || (lock ? groups.selectByIdAndUserIdForUpdate(id, owner)
                    : groups.selectByIdAndUserId(id, owner)) == null) { throw exception(GROUP_CONVERSATION_NOT_EXISTS); }
        }
    }
    /** 不从命令切换租户；验证当前宿主作用域已与可信上下文一致。 */
    private Long user(AiInvocationContext context) {
        if (context == null || !"platform".equals(context.getNamespace()) || !StringUtils.hasText(context.getInvocationId())
                || TenantContextHolder.isIgnore() || TenantContextHolder.getTenantId() == null
                || !TenantContextHolder.getTenantId().toString().equals(context.getTenantId())
                || context.getActorId() == null || !context.getActorId().matches("[1-9][0-9]{0,18}")) {
            throw new IllegalStateException("会话管理宿主身份不匹配");
        }
        try { return Long.valueOf(context.getActorId()); }
        catch (NumberFormatException ex) { throw new IllegalStateException("会话管理宿主身份不匹配"); }
    }
    /** 缺模式不得隐式落到群聊表。 */
    private void requireMode(AiChatMode mode) { if (mode == null) { throw new IllegalArgumentException("聊天模式缺失"); } }
}