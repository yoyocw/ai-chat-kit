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
 * AI 群聊消息持久化对象，保存用户问题、各智能体回复及工作流生成终态。
 */
@TableName("ai_group_chat_message")
@KeySequence("ai_group_chat_message_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiGroupChatMessageDO extends BaseDO {

    /** 群聊消息主键编号；生成中的首条助手消息同时作为停止任务编号。 */
    @TableId
    private Long id;
    /** 所属群聊会话编号。 */
    private Long conversationId;
    /** 消息所属后台用户编号。 */
    private Long userId;
    /** 消息角色：user 表示用户，assistant 表示智能体。 */
    private String role;
    /** 实际发言智能体编码；用户消息和生成占位消息为空。 */
    private String speakerCode;
    /** 实际发言智能体展示名称，由服务端成员目录生成。 */
    private String speakerName;
    /** 本轮编排中的发言顺序，从 1 开始；用户消息为空。 */
    private Integer roundNo;
    /** 用户问题或智能体回复正文。 */
    private String content;
    /** 生成状态：0生成中、1已完成、2已停止、3失败。 */
    private Integer status;
    /** 百炼请求编号，用于问题追踪。 */
    private String requestId;
    /** 服务端白名单构造的版本化群聊扩展结果 JSON；未返回时为空。 */
    private String responseData;
    /** 生成失败原因，成功时为空。 */
    private String errorMessage;
}
