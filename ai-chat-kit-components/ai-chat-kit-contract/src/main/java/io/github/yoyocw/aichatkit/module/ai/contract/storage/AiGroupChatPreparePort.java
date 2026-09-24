package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.List;

/** AI 自有群聊同步准备和停止存储，不包含模型 HTTP、工具授权或接口响应。 */
public interface AiGroupChatPreparePort {
    /** 在发送方真实事务中创建会话及已校验成员关系；失败全部回滚。 */
    Long create(AiInvocationContext context, String title, List<String> memberCodes);
    /**
     * 锁会话、校验成员目录、清理失联轮次、计算历史及写入占位，必须加入外层事务。
     * @param context 已认证并已授权应用的上下文
     * @param conversationId 群聊会话编号
     * @param content 本轮非空问题，最多10000字符
     * @param appId 本轮已授权模型应用
     * @param staleTimeoutSeconds 真实模型读取超时加30秒
     * @return 仅可在外层事务提交后执行的群聊快照
     */
    AiGroupChatPreparedTurn prepare(AiInvocationContext context, Long conversationId, String content, String appId, long staleTimeoutSeconds);
    /** 在外层停止事务中按当前用户归属执行生成态 CAS。 */
    AiSingleChatStopResult stop(AiInvocationContext context, Long messageId);
}
