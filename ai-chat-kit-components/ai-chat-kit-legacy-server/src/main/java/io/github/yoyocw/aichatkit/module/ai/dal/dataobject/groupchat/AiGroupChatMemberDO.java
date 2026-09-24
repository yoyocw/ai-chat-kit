package io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 群聊成员持久化对象，记录前端允许百炼工作流参与编排的候选智能体。
 */
@TableName("ai_group_chat_member")
@KeySequence("ai_group_chat_member_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiGroupChatMemberDO extends BaseDO {

    /** 群聊成员记录主键编号。 */
    @TableId
    private Long id;
    /** 所属群聊会话编号。 */
    private Long conversationId;
    /** 群聊所属后台用户编号，用于租户内归属校验。 */
    private Long userId;
    /** 百炼工作流识别的稳定智能体编码。 */
    private String agentCode;
    /** 成员展示顺序，从 0 开始且在同一群聊内唯一。 */
    private Integer sortOrder;
}
