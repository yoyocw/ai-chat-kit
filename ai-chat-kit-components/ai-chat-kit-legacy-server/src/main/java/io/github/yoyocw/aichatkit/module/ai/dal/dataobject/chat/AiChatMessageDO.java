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

/**
 * AI 对话消息持久化对象，保存用户问题、百炼回复及版本化结构化扩展结果。
 */
@TableName("ai_chat_message")
@KeySequence("ai_chat_message_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiChatMessageDO extends BaseDO {

    /** 消息主键编号，由 PostgreSQL 序列生成。 */
    @TableId
    private Long id;
    /** 所属会话编号，关联 ai_chat_conversation.id。 */
    private Long conversationId;
    /** 消息所属后台用户编号，用于异步流式回写时校验数据归属。 */
    private Long userId;
    /** 消息角色，仅允许 user 或 assistant。 */
    private String role;
    /** 消息正文，使用 TEXT 存储完整问题或模型回复。 */
    private String content;
    /** 是否要求百炼应用返回地图业务结果。 */
    private Boolean mapEnabled;
    /** 回复生成状态：0 生成中、1 已完成、2 已停止、3 失败；用户消息固定为 1。 */
    private Integer status;
    /** 百炼平台请求编号，用于问题排查和平台日志追踪。 */
    private String requestId;
    /** 服务端白名单构造的版本化扩展结果 JSON；旧记录可能仍为历史无版本结构。 */
    private String responseData;
    /** 生成失败原因，成功消息为空，最长 1000 字符。 */
    private String errorMessage;
}
