package io.github.yoyocw.aichatkit.module.ai.service.groupchat;

import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatAgentRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationCreateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatAgentRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatConversationUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatMemberUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatMessageRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.groupchat.vo.AiGroupChatShareRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationPinReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareRespVO;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * AI 群聊服务接口，提供群聊会话、成员、历史消息及百炼多智能体编排调用能力。
 */
public interface AiGroupChatService {

    /**
     * 获得数据库中当前启用的群聊智能体目录。
     *
     * @return 按目录顺序排列的可用智能体
     */
    List<AiGroupChatAgentRespVO> getAgentList();

    /**
     * 创建当前用户群聊并保存候选成员。
     *
     * @param reqVO 群聊标题和成员编码
     * @param userId 登录用户编号
     * @return 新群聊会话编号
     */
    Long createConversation(AiGroupChatConversationCreateReqVO reqVO, Long userId);

    /**
     * 重命名当前用户群聊。
     *
     * @param reqVO 会话编号和新标题
     * @param userId 登录用户编号
     */
    void updateConversation(AiGroupChatConversationUpdateReqVO reqVO, Long userId);

    /**
     * 更新当前用户群聊的置顶状态，生成期间允许操作。
     *
     * @param reqVO 群聊编号和目标置顶状态
     * @param userId 登录用户编号
     */
    void pinConversation(AiChatConversationPinReqVO reqVO, Long userId);

    /**
     * 创建或复用当前用户群聊仍在有效期内的公开分享。
     *
     * @param reqVO 群聊编号与可选有效天数
     * @param userId 登录用户编号
     * @return 不可枚举分享码、完整地址及毫秒级过期时间
     */
    AiChatConversationShareRespVO shareConversation(AiChatConversationShareReqVO reqVO, Long userId);

    /**
     * 取消当前用户群聊的公开分享，未分享时保持幂等成功。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     */
    void cancelConversationShare(Long id, Long userId);

    /**
     * 跨租户读取公开分享群聊，只返回固定成员目录字段和已完成消息。
     *
     * @param shareCode 不可枚举分享码
     * @return 脱敏群聊内容
     */
    AiGroupChatShareRespVO getSharedConversation(String shareCode);

    /**
     * 调整当前用户群聊候选成员，生成期间禁止修改。
     *
     * @param reqVO 会话编号和完整成员编码集合
     * @param userId 登录用户编号
     */
    void updateMembers(AiGroupChatMemberUpdateReqVO reqVO, Long userId);

    /**
     * 删除当前用户群聊、成员和消息；活动工作流会先被停止。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     */
    void deleteConversation(Long id, Long userId);

    /**
     * 查询当前用户全部群聊。
     *
     * @param userId 登录用户编号
     * @return 按最近更新时间倒序的群聊列表
     */
    List<AiGroupChatConversationRespVO> getConversationList(Long userId);

    /**
     * 查询群聊历史消息。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     * @return 按发送顺序排列的消息列表
     */
    List<AiGroupChatMessageRespVO> getMessageList(Long conversationId, Long userId);

    /**
     * 保存用户问题并通过 SSE 返回百炼工作流编排的多智能体回复。
     *
     * @param reqVO 群聊会话编号和问题正文
     * @param userId 登录用户编号
     * @return Spring MVC 流式响应体
     */
    StreamingResponseBody sendMessage(AiGroupChatSendReqVO reqVO, Long userId);

    /**
     * 停止仍在生成中的群聊工作流。
     *
     * @param messageId SSE start 事件返回的生成占位消息编号
     * @param userId 登录用户编号
     */
    void stopMessage(Long messageId, Long userId);
}
