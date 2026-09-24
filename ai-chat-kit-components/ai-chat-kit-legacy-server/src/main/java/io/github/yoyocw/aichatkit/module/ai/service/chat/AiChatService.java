package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationPinReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationShareRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatConversationUpdateReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatMessageRespVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatSendReqVO;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatShareRespVO;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * AI 智能对话服务接口，提供会话管理、消息历史和百炼流式问答能力。
 * 所有会话与消息操作均按登录用户隔离，禁止访问其他用户数据。
 */
public interface AiChatService {

    /**
     * 创建空白对话会话。
     *
     * @param userId 登录用户编号
     * @return 新建会话编号
     */
    Long createConversation(Long userId);

    /**
     * 重命名当前用户的会话。
     *
     * @param reqVO 会话编号与新标题
     * @param userId 登录用户编号
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 会话不存在或不属于当前用户时抛出
     */
    void updateConversation(AiChatConversationUpdateReqVO reqVO, Long userId);

    /**
     * 置顶或取消置顶当前用户的会话。
     *
     * @param reqVO 会话编号与目标置顶状态
     * @param userId 登录用户编号
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 会话不存在或不属于当前用户时抛出
     */
    void pinConversation(AiChatConversationPinReqVO reqVO, Long userId);

    /**
     * 创建或复用当前用户会话仍在有效期内的公开分享。
     *
     * @param reqVO 会话编号与可选有效天数
     * @param userId 登录用户编号
     * @return 分享码、服务端生成的完整地址和毫秒级过期时间
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 会话不存在或不属于当前用户时抛出
     */
    AiChatConversationShareRespVO shareConversation(AiChatConversationShareReqVO reqVO, Long userId);

    /**
     * 取消当前用户会话的公开分享；未分享时按幂等成功处理。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 会话不存在或不属于当前用户时抛出
     */
    void cancelConversationShare(Long id, Long userId);

    /**
     * 按不可枚举分享码公开读取会话，仅返回标题和已完成消息安全字段。
     *
     * @param shareCode 32 位分享码
     * @return 脱敏后的公开会话
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 分享不存在、已取消或会话已删除时抛出
     */
    AiChatShareRespVO getSharedConversation(String shareCode);

    /**
     * 删除当前用户的会话及其全部消息，并停止仍在生成的请求。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 会话不存在或不属于当前用户时抛出
     */
    void deleteConversation(Long id, Long userId);

    /**
     * 查询当前用户的历史会话。
     *
     * @param userId 登录用户编号
     * @return 按最近更新时间倒序排列的会话列表
     */
    List<AiChatConversationRespVO> getConversationList(Long userId);

    /**
     * 查询当前用户指定会话的完整消息历史。
     *
     * @param conversationId 会话编号
     * @param userId 登录用户编号
     * @return 按消息创建顺序排列的消息列表
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 会话不存在或不属于当前用户时抛出
     */
    List<AiChatMessageRespVO> getMessageList(Long conversationId, Long userId);

    /**
     * 保存用户问题并创建百炼 SSE 流式响应。
     *
     * @param reqVO 会话、问题和地图显示开关
     * @param userId 登录用户编号
     * @return Spring MVC 流式响应体，事件依次为 start、progress、delta、可选 result、done 或 error
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 配置无效、会话越权或已有回复生成中时抛出
     */
    StreamingResponseBody sendMessage(AiChatSendReqVO reqVO, Long userId);

    /**
     * 停止当前用户指定的生成中助手消息。
     *
     * @param messageId 助手消息编号
     * @param userId 登录用户编号
     * @throws io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException 消息不存在、越权或不在生成中时抛出
     */
    void stopMessage(Long messageId, Long userId);
}
