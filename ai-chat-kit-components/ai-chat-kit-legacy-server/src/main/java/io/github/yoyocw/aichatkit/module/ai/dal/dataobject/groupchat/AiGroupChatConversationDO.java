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

import java.time.LocalDateTime;

/**
 * AI 群聊会话持久化对象，保存用户群聊标题和百炼工作流短期会话标识。
 */
@TableName("ai_group_chat_conversation")
@KeySequence("ai_group_chat_conversation_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiGroupChatConversationDO extends BaseDO {

    /** 群聊会话主键编号，由 PostgreSQL 序列生成。 */
    @TableId
    private Long id;
    /** 群聊所属后台用户编号，仅该用户可访问。 */
    private Long userId;
    /** 群聊展示标题，长度为 1 至 30 个字符。 */
    private String title;
    /** 百炼编排工作流短期会话标识，失效后清空并携带本地历史摘要重试。 */
    private String bailianSessionId;
    /** 远端会话所属应用；未知来源的历史会话在下次发送时重建。 */
    private String bailianAppId;
    /** 当前助手占位消息编号，防止迟到调用覆盖或清除新一轮会话。 */
    private Long bailianTurnId;
    /** 已折叠旧消息形成的抽取式滚动记忆，不包含最近消息窗口。 */
    private String memorySummary;
    /** 已进入滚动记忆的最大消息编号；为空表示尚未折叠历史。 */
    private Long memoryCursorMessageId;
    /** 是否置顶显示；true 置顶，false 普通排序。 */
    private Boolean pinned;
    /** 最近一次置顶时间；未置顶时为空。 */
    private LocalDateTime pinnedTime;
    /** 公开分享码；未分享或已取消分享时为空。 */
    private String shareCode;
    /** 分享状态：0 关闭，1 启用；启用时还必须满足分享未过期。 */
    private Integer shareStatus;
    /** 分享过期时间；启用分享时非空，超过该时间后匿名访问失效。 */
    private LocalDateTime shareExpireTime;
    /** 分享成功访问次数，只统计完成匿名响应组装且仍未过期的访问。 */
    private Long shareAccessCount;
    /** 最近一次成功匿名访问时间；从未访问时为空。 */
    private LocalDateTime shareLastAccessTime;
}
