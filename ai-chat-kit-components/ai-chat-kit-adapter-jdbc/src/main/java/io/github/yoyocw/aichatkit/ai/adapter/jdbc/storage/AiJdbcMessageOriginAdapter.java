package io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcAccess.SCOPE;

/** 同事务记录不可变来源，复核时始终匹配完整来源维度，不以用户相同替代调用方归属。 */
public final class AiJdbcMessageOriginAdapter implements AiMessageOriginPort {
    /** 同数据源事务及完整身份参数。 */
    private final AiJdbcAccess access;
    /** 宿主真实同步入口来源，普通入口可明确返回空。 */
    private final AiOriginContextPort originContext;

    /** 不提供虚构来源默认实现；宿主必须明确接入真实来源捕获。 */
    public AiJdbcMessageOriginAdapter(AiJdbcAccess access, AiOriginContextPort originContext) {
        this.access = access; this.originContext = originContext;
    }

    /** 来源写入必须与真实助手占位同事务，失败不降级为无来源请求。 */
    @Override
    public void record(AiInvocationContext context, AiChatMode mode, Long messageId, String appId) {
        access.requireTransaction(); access.scope(context);
        AiCallerOrigin origin = originContext.capture(appId);
        if (origin == null) { return; }
        validate(context, origin, messageId);
        if (appId == null || appId.trim().isEmpty()) { throw new IllegalArgumentException("实际应用缺失"); }
        String modeCode = AiJdbcExecutionAuditAdapter.mode(mode);
        // INSERT SELECT 同时验证助手占位归属和生成态；唯一冲突交外层事务回滚。
        int inserted = access.jdbc().update("INSERT INTO ai_runtime_origin(namespace,tenant_id,actor_id,mode,message_id,client_record_id,client_id,business_system,environment,app_id)"
                + " SELECT namespace,tenant_id,actor_id,mode,id,?,?,?,?,? FROM ai_runtime_message WHERE "
                + SCOPE + " AND mode=? AND id=? AND role='assistant' AND status=0",
                io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcChatRepository.parameters(new Object[]{origin.getClientRecordIdentifier(),
                        origin.getClientId(), origin.getBusinessSystem(), origin.getEnvironment(), appId},
                        access.scope(context).args(modeCode, messageId)));
        if (inserted != 1) { throw new IllegalStateException("委托来源助手占位归属无效"); }
    }

    /** 只返回已核对的历史应用，不授予停止或跨会话访问能力。 */
    @Override
    public String verify(AiInvocationContext context, AiCallerOrigin caller, AiChatMode mode, Long messageId, Set<String> allowedAppIds) {
        access.requireTransaction(); validate(context, caller, messageId);
        if (allowedAppIds == null || allowedAppIds.isEmpty()) { throw new IllegalStateException("委托应用范围为空"); }
        List<String> apps = access.jdbc().query("SELECT app_id FROM ai_runtime_origin WHERE " + SCOPE
                + " AND mode=? AND message_id=? AND client_record_id=? AND client_id=? AND business_system=? AND environment=?",
                (rs, row) -> rs.getString(1), access.scope(context).args(AiJdbcExecutionAuditAdapter.mode(mode), messageId,
                        caller.getClientRecordIdentifier(), caller.getClientId(), caller.getBusinessSystem(), caller.getEnvironment()));
        if (apps.size() != 1 || !allowedAppIds.contains(apps.get(0))) { throw new IllegalStateException("委托消息来源缺失或不匹配"); }
        return apps.get(0);
    }

    /** 标识完全匹配，禁止不透明身份隐式转换为林业数字编号。 */
    private void validate(AiInvocationContext context, AiCallerOrigin origin, Long messageId) {
        access.scope(context);
        if (origin == null || messageId == null || messageId <= 0
                || !Objects.equals(context.getTenantId(), origin.getTenantIdentifier())
                || !Objects.equals(context.getActorId(), origin.getActorIdentifier())) {
            throw new IllegalStateException("委托来源身份不匹配");
        }
    }
}
