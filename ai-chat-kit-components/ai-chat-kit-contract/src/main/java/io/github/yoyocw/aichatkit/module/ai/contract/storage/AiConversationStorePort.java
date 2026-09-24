package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.List;

/** AI 自有会话管理与历史查询，权限由同步宿主入口核验，存储始终限制完整归属。 */
public interface AiConversationStorePort {
    /** 同调用方事务创建默认标题单聊，写入完整身份归属，不创建消息或调用模型。 */
    default Long createSingle(AiInvocationContext context) {
        throw new IllegalStateException("宿主尚未实现单聊会话创建");
    }
    /** @return 当前身份全部会话，新存储按置顶、置顶时间、更新时间、id依次倒序，原表适配保留原排序；不隐式截断 */
    List<AiConversationView> list(AiInvocationContext context, AiChatMode mode);
    /** @return 已验证归属的全部消息，按id升序；会话不存在或无权访问时抛异常 */
    List<AiMessageView> messages(AiInvocationContext context, AiChatMode mode, Long conversationId);
    /** 必须加入调用方事务；单聊标题1至100字符，群聊1至30字符，保存前去首尾空格。 */
    void rename(AiInvocationContext context, AiChatMode mode, Long conversationId, String title);
    /** 必须加入调用方事务；取消置顶同时清空置顶时间。 */
    void pin(AiInvocationContext context, AiChatMode mode, Long conversationId, boolean pinned);
    /**
     * 同事务逻辑删除会话、消息、成员，收口仍在运行的消息及审计；保留不可变来源和审计记录。
     * @return 删除前仍在生成的助手占位id，调用方只可在事务成功提交后取消模型
     */
    List<Long> delete(AiInvocationContext context, AiChatMode mode, Long conversationId);
    /**
     * 会话行锁下拒绝生成中的群聊，验证可信目录后替换有序成员；清理远端session/turn，保留本地历史。
     * 必须加入调用方事务；新成员2至3个且不得重复。
     */
    void updateGroupMembers(AiInvocationContext context, Long conversationId, List<String> memberCodes);
}
