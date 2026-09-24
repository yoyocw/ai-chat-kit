package io.github.yoyocw.aichatkit.module.ai.contract.share;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** AI 自有公开分享存储，公开读取仅接受随机码，不能切换为任意会话编号读取。 */
public interface AiConversationSharePort {
    /**
     * 必须加入调用方真实事务并锁定归属会话；已有有效分享复用且不延长期限。
     * @param context 已认证且通过分享权限核验的身份
     * @param mode 固定聊天模式
     * @param conversationId 当前用户会话编号
     * @param validDays 已验证1至30天
     * @param candidateCode 安全随机生成的32位小写hex候选码
     * @return 实际分享码与数据库到期时刻
     */
    AiShareLease issue(AiInvocationContext context, AiChatMode mode, Long conversationId, int validDays, String candidateCode);
    /** 锁定当前用户会话后撤销分享并清码，保留历史访问统计。 */
    void revoke(AiInvocationContext context, AiChatMode mode, Long conversationId);
    /**
     * 在固定部署命名空间内按mode+code读取动态已完成消息，数据归属只从分享记录取得。
     * 最后必须以存储实现固定的可信时钟复核有效性并原子计数，失效不返回已组装内容。
     * JDBC使用数据库clock_timestamp，原MyBatis使用既有服务端时钟，不从请求指定时刻。
     * 不要求访客登录，也不得伪造创建者认证上下文。
     */
    AiSharedConversation readPublic(AiChatMode mode, String shareCode);
}
