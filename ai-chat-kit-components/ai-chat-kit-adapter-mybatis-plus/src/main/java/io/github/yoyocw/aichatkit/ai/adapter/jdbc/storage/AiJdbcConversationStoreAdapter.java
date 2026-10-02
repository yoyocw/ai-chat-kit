package io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcConversationRepository;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationStorePort;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMessageView;
import java.util.List;

/** AI 会话管理端口的 PostgreSQL 实现，修改由数据访问层核对真实外层事务。 */
public final class AiJdbcConversationStoreAdapter implements AiConversationStorePort {
    /** 与执行存储共用归属条件和会话行锁。 */
    private final AiJdbcConversationRepository repository;
    /** @param repository AI 自有会话数据访问 */
    public AiJdbcConversationStoreAdapter(AiJdbcConversationRepository repository) { this.repository = repository; }

    /** 独立创建真实空单聊会话，归属及外层事务由 repository 复核。 */
    @Override
    public Long createSingle(AiInvocationContext context) { return repository.createSingle(context); }

    /** 返回全部已归属过滤会话，不追加未声明分页限制。 */
    @Override
    public List<AiConversationView> list(AiInvocationContext context, AiChatMode mode) { return repository.list(context, mode); }
    /** 会话归属检查与消息条件同时保留。 */
    @Override
    public List<AiMessageView> messages(AiInvocationContext context, AiChatMode mode, Long id) { return repository.messages(context, mode, id); }
    /** 真实事务内仅修改用户可编辑标题。 */
    @Override
    public void rename(AiInvocationContext context, AiChatMode mode, Long id, String title) { repository.rename(context, mode, id, title); }
    /** 同事务更新置顶状态与时间。 */
    @Override
    public void pin(AiInvocationContext context, AiChatMode mode, Long id, boolean pinned) { repository.pin(context, mode, id, pinned); }
    /** 只返回提交后待取消的占位编号，不在数据库适配器里取消模型。 */
    @Override
    public List<Long> delete(AiInvocationContext context, AiChatMode mode, Long id) { return repository.delete(context, mode, id); }
    /** 生成中拒绝成员修改，已完成历史保留。 */
    @Override
    public void updateGroupMembers(AiInvocationContext context, Long id, List<String> codes) { repository.updateGroupMembers(context, id, codes); }
}
