package io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat;

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
 * AI 对话会话持久化对象，记录当前用户的会话标题与百炼短期会话标识。
 */
@TableName("ai_chat_conversation")
@KeySequence("ai_chat_conversation_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiChatConversationDO extends BaseDO {

    /** 会话主键编号，由 PostgreSQL 序列生成。 */
    @TableId
    private Long id;
    /** 会话所属后台用户编号，仅该用户可查询或修改。 */
    private Long userId;
    /** 会话标题，首次提问时自动截取，允许用户后续重命名，最长 100 字符。 */
    private String title;
    /** 百炼 Agent 2.0 会话标识；首轮为空，后续用于延续一小时内的云端上下文。 */
    private String bailianSessionId;
    /** 远端会话所属应用；旧记录为空时不得复用未知来源的远端会话。 */
    private String bailianAppId;
    /** 当前远端调用对应的助手消息编号，防止超时旧调用覆盖新一轮会话。 */
    private Long bailianTurnId;
    /** 已折叠旧消息形成的抽取式滚动记忆，不包含最近消息窗口。 */
    private String memorySummary;
    /** 已进入滚动记忆的最大消息编号；为空表示尚未折叠历史。 */
    private Long memoryCursorMessageId;
    /** 是否置顶显示；true 置顶，false 按普通会话排序。 */
    private Boolean pinned;
    /** 最近一次置顶时间；取消置顶时为空，用于多个置顶会话排序。 */
    private LocalDateTime pinnedTime;
    /** 公开分享码；关闭分享时为空，启用时使用不可枚举的 32 位随机十六进制字符串。 */
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
