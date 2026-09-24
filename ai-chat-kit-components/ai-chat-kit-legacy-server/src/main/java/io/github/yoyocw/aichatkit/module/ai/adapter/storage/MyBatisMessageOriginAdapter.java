package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.origin.AiMessageOriginDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.origin.AiMessageOriginMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import org.springframework.util.StringUtils;
import java.util.Objects;
import java.util.Set;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.ROLE_ASSISTANT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_GENERATING;

/** 在调用方事务内写入或复查来源，任何校验或数据库错误均交外层事务回滚。 */
@Service
@RequiredArgsConstructor
public class MyBatisMessageOriginAdapter implements AiMessageOriginPort {
    /** 已核验的同源事务绑定，来源校验不得参与其它管理器的事务。 */
    private final AiTransactionExecutor transactions;
    /** 同步宿主来源，不获取原始凭据。 */
    private final AiOriginContextPort originContext;
    /** 无更新或清理方法的来源 Mapper。 */
    private final AiMessageOriginMapper originMapper;
    /** 单聊助手占位归属查询，沿用租户过滤。 */
    private final AiChatMessageMapper messageMapper;
    /** 群聊请求占位归属查询，不记录后续成员回复。 */
    private final AiGroupChatMessageMapper groupMessageMapper;

    @Override
    public void record(AiInvocationContext context, AiChatMode mode, Long messageId, String appId) {
        transactions.mandatory(() -> {
            AiCallerOrigin origin = originContext.capture(appId);
            if (origin == null) { return null; }
            validate(context, origin, mode, messageId, appId);
            if (!isPlaceholder(mode, messageId, origin.getUserId())) {
                throw new IllegalStateException("委托来源助手占位归属无效");
            }
            // 不捕获重复键或缺表错误，也不回退到无来源发送。
            int inserted = originMapper.insert(new AiMessageOriginDO(origin.getTenantId(), origin.getUserId(),
                    mode == AiChatMode.SINGLE ? "single" : "group", messageId, origin.getClientRecordId(),
                    origin.getClientId(), origin.getBusinessSystem(), origin.getEnvironment(), appId));
            if (inserted != 1) { throw new IllegalStateException("委托来源写入失败"); }
            return null;
        });
    }

    @Override
    public String verify(AiInvocationContext context, AiCallerOrigin caller, AiChatMode mode,
                         Long messageId, Set<String> allowedAppIds) {
        return transactions.mandatory(() -> {
            validateContext(context, caller, mode, messageId);
            if (allowedAppIds == null || allowedAppIds.isEmpty()
                    || allowedAppIds.stream().anyMatch(id -> id == null || !id.matches("[a-fA-F0-9]{32}"))) {
                throw new IllegalStateException("委托来源应用授权无效");
            }
            // 显式使用可信租户与固定模式查询；来源表不依赖自动租户拦截。
            String sourceMode = mode == AiChatMode.SINGLE ? "single" : "group";
            AiMessageOriginDO source = originMapper.selectOrigin(caller.getTenantId(), sourceMode, messageId);
            if (source == null || !Objects.equals(source.getTenantId(), caller.getTenantId())
                    || !Objects.equals(source.getMode(), sourceMode) || !Objects.equals(source.getMessageId(), messageId)
                    || !Objects.equals(source.getUserId(), caller.getUserId())
                    || !Objects.equals(source.getClientRecordId(), caller.getClientRecordId())
                    || !Objects.equals(source.getClientId(), caller.getClientId())
                    || !Objects.equals(source.getBusinessSystem(), caller.getBusinessSystem())
                    || !Objects.equals(source.getEnvironment(), caller.getEnvironment())
                    || source.getAppId() == null || !allowedAppIds.contains(source.getAppId())) {
                throw new IllegalStateException("委托消息来源缺失或不匹配");
            }
            // 仅返回历史应用，不据此授予停止权限或修改消息状态。
            return source.getAppId();
        });
    }

    /** 来源与同步宿主身份必须一致，禁止忽略租户过滤。 */
    private void validate(AiInvocationContext context, AiCallerOrigin origin, AiChatMode mode,
                          Long messageId, String appId) {
        validateContext(context, origin, mode, messageId);
        if (appId == null || !appId.matches("[a-fA-F0-9]{32}")) {
            throw new IllegalStateException("委托来源与当前发送不匹配");
        }
    }

    /** 记录及复查共同校验可信上下文；参数本身不构成认证证明。 */
    private void validateContext(AiInvocationContext context, AiCallerOrigin origin, AiChatMode mode,
                                 Long messageId) {
        if (context == null || origin == null || !"platform".equals(context.getNamespace())
                || !StringUtils.hasText(context.getInvocationId()) || TenantContextHolder.isIgnore()
                || !Objects.equals(origin.getTenantId(), TenantContextHolder.getTenantId())
                || !origin.getTenantId().toString().equals(context.getTenantId())
                || !origin.getUserId().toString().equals(context.getActorId())
                || mode == null || messageId == null || messageId <= 0) {
            throw new IllegalStateException("委托来源与当前发送不匹配");
        }
    }

    /** 两类表分别按用户查询，仅生成中的助手占位可绑定来源。 */
    private boolean isPlaceholder(AiChatMode mode, Long messageId, Long userId) {
        if (mode == AiChatMode.SINGLE) {
            AiChatMessageDO message = messageMapper.selectByIdAndUserId(messageId, userId);
            return message != null && ROLE_ASSISTANT.equals(message.getRole())
                    && Objects.equals(STATUS_GENERATING, message.getStatus());
        }
        AiGroupChatMessageDO message = groupMessageMapper.selectByIdAndUserId(messageId, userId);
        return message != null && ROLE_ASSISTANT.equals(message.getRole())
                && Objects.equals(STATUS_GENERATING, message.getStatus());
    }
}
