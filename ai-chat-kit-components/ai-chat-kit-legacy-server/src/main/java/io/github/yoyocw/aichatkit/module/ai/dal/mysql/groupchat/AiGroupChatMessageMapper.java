package io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.ROLE_ASSISTANT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.ROLE_USER;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_COMPLETED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_GENERATING;

/**
 * AI 群聊消息数据访问接口，提供历史查询、生成互斥与终态条件更新能力。
 */
@Mapper
public interface AiGroupChatMessageMapper extends BaseMapperX<AiGroupChatMessageDO> {

    /**
     * 会话锁后真实读取并锁定助手目标；清理一级缓存，避免后续停止复用锁前快照。
     * @param tenantId 当前可信租户
     * @param id 正数消息ID
     * @param userId 当前可信用户
     * @return 仅停止校验所需字段；不存在或已删除时为null
     */
    @Select("SELECT id, conversation_id, user_id, role, status FROM ai_group_chat_message "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND user_id = #{userId} AND deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    AiGroupChatMessageDO selectStopTargetForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id,
                                                  @Param("userId") Long userId);

    /**
     * 按消息编号正序查询群聊全部消息。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     * @return 群聊消息列表
     */
    default List<AiGroupChatMessageDO> selectListByConversationId(Long conversationId, Long userId) {
        return selectList(new LambdaQueryWrapperX<AiGroupChatMessageDO>()
                .eq(AiGroupChatMessageDO::getConversationId, conversationId)
                .eq(AiGroupChatMessageDO::getUserId, userId)
                .orderByAsc(AiGroupChatMessageDO::getId));
    }

    /**
     * 跨租户查询公开群聊中的已完成消息，按消息编号正序返回。
     *
     * @param conversationId 群聊会话编号
     * @return 仅包含 user 和 assistant 角色的已完成消息
     */
    default List<AiGroupChatMessageDO> selectCompletedListByConversationId(Long conversationId) {
        return selectList(new LambdaQueryWrapperX<AiGroupChatMessageDO>()
                .eq(AiGroupChatMessageDO::getConversationId, conversationId)
                .eq(AiGroupChatMessageDO::getStatus, STATUS_COMPLETED)
                .in(AiGroupChatMessageDO::getRole, ROLE_USER, ROLE_ASSISTANT)
                .orderByAsc(AiGroupChatMessageDO::getId));
    }

    /**
     * 查询最近二十条已完成消息，用于构建工作流本地历史摘要。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     * @return 按消息编号倒序的最近消息
     */
    default List<AiGroupChatMessageDO> selectRecentCompleted(Long conversationId, Long userId) {
        return selectList(new LambdaQueryWrapperX<AiGroupChatMessageDO>()
                .eq(AiGroupChatMessageDO::getConversationId, conversationId)
                .eq(AiGroupChatMessageDO::getUserId, userId)
                .eq(AiGroupChatMessageDO::getStatus, STATUS_COMPLETED)
                .orderByDesc(AiGroupChatMessageDO::getId)
                .last("LIMIT 20"));
    }

    /**
     * 查询用户拥有的指定群聊消息。
     *
     * @param id 消息编号
     * @param userId 登录用户编号
     * @return 匹配消息，不存在时返回 null
     */
    default AiGroupChatMessageDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(AiGroupChatMessageDO::getId, id, AiGroupChatMessageDO::getUserId, userId);
    }

    /**
     * 统计群聊中仍在生成的占位助手消息。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     * @return 生成中消息数量
     */
    default Long selectGeneratingCount(Long conversationId, Long userId) {
        return selectCount(new LambdaQueryWrapperX<AiGroupChatMessageDO>()
                .eq(AiGroupChatMessageDO::getConversationId, conversationId)
                .eq(AiGroupChatMessageDO::getUserId, userId)
                .eq(AiGroupChatMessageDO::getRole, ROLE_ASSISTANT)
                .eq(AiGroupChatMessageDO::getStatus, STATUS_GENERATING));
    }

    /**
     * 将超过调用总超时仍未收口的群聊生成任务标记失败。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     * @param expiredBefore 早于该时间的生成消息视为异常中断
     */
    default void failStaleGenerating(Long conversationId, Long userId, LocalDateTime expiredBefore) {
        AiGroupChatMessageDO updateMessage = AiGroupChatMessageDO.builder().status(STATUS_FAILED)
                .errorMessage("服务中断，群聊生成任务未正常结束").build();
        update(updateMessage, new LambdaUpdateWrapper<AiGroupChatMessageDO>()
                .eq(AiGroupChatMessageDO::getConversationId, conversationId)
                .eq(AiGroupChatMessageDO::getUserId, userId)
                .eq(AiGroupChatMessageDO::getRole, ROLE_ASSISTANT)
                .eq(AiGroupChatMessageDO::getStatus, STATUS_GENERATING)
                .lt(AiGroupChatMessageDO::getCreateTime, expiredBefore));
    }

    /**
     * 仅当占位消息仍为生成中时切换终态，避免停止、完成和失败互相覆盖。
     *
     * @param message 需要回写的消息终态字段
     * @return 是否成功从生成中切换
     */
    default boolean updateGeneratingMessage(AiGroupChatMessageDO message) {
        return update(message, new LambdaUpdateWrapper<AiGroupChatMessageDO>()
                .eq(AiGroupChatMessageDO::getId, message.getId())
                .eq(AiGroupChatMessageDO::getStatus, STATUS_GENERATING)) > 0;
    }

    /**
     * 删除群聊全部消息，用于删除会话。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     */
    default void deleteByConversationId(Long conversationId, Long userId) {
        delete(new LambdaQueryWrapperX<AiGroupChatMessageDO>()
                .eq(AiGroupChatMessageDO::getConversationId, conversationId)
                .eq(AiGroupChatMessageDO::getUserId, userId));
    }
}
