package io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.ROLE_ASSISTANT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_COMPLETED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_GENERATING;

/**
 * AI 对话消息数据访问接口，提供会话内顺序查询与生成状态检查能力。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapperX<AiChatMessageDO> {

    /**
     * 会话锁后真实读取并锁定助手目标；清理一级缓存，避免后续停止复用锁前快照。
     * @param tenantId 当前可信租户
     * @param id 正数消息ID
     * @param userId 当前可信用户
     * @return 仅停止校验所需字段；不存在或已删除时为null
     */
    @Select("SELECT id, conversation_id, user_id, role, status FROM ai_chat_message "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND user_id = #{userId} AND deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    AiChatMessageDO selectStopTargetForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                             @Param("userId") Long userId);

    /**
     * 查询会话的全部消息，按消息编号正序返回以还原上下文。
     *
     * @param conversationId 会话编号
     * @param userId 登录用户编号
     * @return 会话消息列表
     */
    default List<AiChatMessageDO> selectListByConversationId(Long conversationId, Long userId) {
        return selectList(new LambdaQueryWrapperX<AiChatMessageDO>()
                .eq(AiChatMessageDO::getConversationId, conversationId)
                .eq(AiChatMessageDO::getUserId, userId)
                .orderByAsc(AiChatMessageDO::getId));
    }

    /**
     * 查询公开分享会话中已完成的消息，过滤生成中、停止和失败内容。
     * 调用方必须在租户忽略上下文中执行。
     *
     * @param conversationId 会话编号
     * @return 按消息编号正序排列的已完成消息
     */
    default List<AiChatMessageDO> selectCompletedListByConversationId(Long conversationId) {
        return selectList(new LambdaQueryWrapperX<AiChatMessageDO>()
                .eq(AiChatMessageDO::getConversationId, conversationId)
                .eq(AiChatMessageDO::getStatus, STATUS_COMPLETED)
                .orderByAsc(AiChatMessageDO::getId));
    }

    /**
     * 查询指定用户消息，防止跨用户停止或读取生成任务。
     *
     * @param id 消息编号
     * @param userId 登录用户编号
     * @return 匹配消息，不存在或不属于该用户时返回 null
     */
    default AiChatMessageDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(AiChatMessageDO::getId, id, AiChatMessageDO::getUserId, userId);
    }

    /**
     * 统计会话中仍在生成的助手消息。
     *
     * @param conversationId 会话编号
     * @param userId 登录用户编号
     * @return 生成中消息数量
     */
    default Long selectGeneratingCount(Long conversationId, Long userId) {
        return selectCount(new LambdaQueryWrapperX<AiChatMessageDO>()
                .eq(AiChatMessageDO::getConversationId, conversationId)
                .eq(AiChatMessageDO::getUserId, userId)
                .eq(AiChatMessageDO::getRole, ROLE_ASSISTANT)
                .eq(AiChatMessageDO::getStatus, STATUS_GENERATING));
    }

    /**
     * 将超过百炼调用总超时仍未收口的生成消息标记失败，恢复进程异常退出后的会话可用性。
     *
     * @param conversationId 会话编号
     * @param userId 登录用户编号
     * @param expiredBefore 早于该时间的生成消息视为失联
     */
    default void failStaleGenerating(Long conversationId, Long userId, LocalDateTime expiredBefore) {
        AiChatMessageDO updateMessage = AiChatMessageDO.builder().status(STATUS_FAILED)
                .errorMessage("服务中断，生成任务未正常结束").build();
        update(updateMessage, new LambdaUpdateWrapper<AiChatMessageDO>()
                .eq(AiChatMessageDO::getConversationId, conversationId)
                .eq(AiChatMessageDO::getUserId, userId)
                .eq(AiChatMessageDO::getRole, ROLE_ASSISTANT)
                .eq(AiChatMessageDO::getStatus, STATUS_GENERATING)
                .lt(AiChatMessageDO::getCreateTime, expiredBefore));
    }

    /**
     * 仅当消息仍在生成中时切换终态，防止停止、完成和失败并发互相覆盖。
     *
     * @param message 消息终态数据
     * @return 是否成功从生成中切换到目标终态
     */
    default boolean updateGeneratingMessage(AiChatMessageDO message) {
        return update(message, new LambdaUpdateWrapper<AiChatMessageDO>()
                .eq(AiChatMessageDO::getId, message.getId())
                .eq(AiChatMessageDO::getStatus, STATUS_GENERATING)) > 0;
    }
}
