package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 群聊发送事务内准备的固定轮次，不包含用户令牌或数据库对象。 */
public final class AiGroupChatPreparedTurn {
    /** 当前会话编号。 */
    private final Long conversationId;
    /** 本轮助手占位编号。 */
    private final Long messageId;
    /** 已校验应用绑定的远端会话，可为空。 */
    private final String sessionId;
    /** 本轮以前的滚动记忆。 */
    private final String history;
    /** 应用变更是否重建模型上下文。 */
    private final boolean appChanged;
    /** 发送时重新核对目录得到的有序成员。 */
    private final List<AiGroupMemberSnapshot> members;

    /** 将发送时的成员和会话状态复制为异步不可变快照。 */
    public AiGroupChatPreparedTurn(Long conversationId, Long messageId, String sessionId, String history,
                                   boolean appChanged, List<AiGroupMemberSnapshot> members) {
        this.conversationId = conversationId; this.messageId = messageId; this.sessionId = sessionId;
        this.history = history; this.appChanged = appChanged;
        this.members = Collections.unmodifiableList(new ArrayList<AiGroupMemberSnapshot>(members));
    }
    /** @return 会话编号 */
    public Long getConversationId() { return conversationId; }
    /** @return 助手占位编号 */
    public Long getMessageId() { return messageId; }
    /** @return 已校验的远端会话 */
    public String getSessionId() { return sessionId; }
    /** @return 本轮前滚动记忆 */
    public String getHistory() { return history; }
    /** @return 是否通知应用切换 */
    public boolean isAppChanged() { return appChanged; }
    /** @return 不可变成员顺序 */
    public List<AiGroupMemberSnapshot> getMembers() { return members; }
}
