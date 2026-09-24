package io.github.yoyocw.aichatkit.ai.adapter.jdbc.storage;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcShareRepository;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiConversationSharePort;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareLease;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedConversation;

/** 公开分享的同源数据库适配，事务重试及宿主身份验证由引擎入口组织。 */
public final class AiJdbcConversationShareAdapter implements AiConversationSharePort {
    /** 固定部署namespace的数据访问能力。 */
    private final AiJdbcShareRepository repository;
    /** @param repository AI 自有分享数据访问 */
    public AiJdbcConversationShareAdapter(AiJdbcShareRepository repository) { this.repository = repository; }
    /** 在会话锁内创建或复用有效分享，唯一冲突向外传播。 */
    @Override
    public AiShareLease issue(AiInvocationContext context, AiChatMode mode, Long id, int days, String code) {
        return repository.issue(context, mode, id, days, code);
    }
    /** 撤销不清理历史访问统计。 */
    @Override
    public void revoke(AiInvocationContext context, AiChatMode mode, Long id) { repository.revoke(context, mode, id); }
    /** 公开只接受mode和随机码，不创建登录上下文。 */
    @Override
    public AiSharedConversation readPublic(AiChatMode mode, String shareCode) { return repository.readPublic(mode, shareCode); }
}
