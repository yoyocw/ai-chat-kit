package io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMemberDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 群聊成员数据访问接口，维护会话候选智能体及其展示顺序。
 */
@Mapper
public interface AiGroupChatMemberMapper extends BaseMapperX<AiGroupChatMemberDO> {

    /**
     * 查询群聊成员并按配置顺序返回。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     * @return 有序成员列表
     */
    default List<AiGroupChatMemberDO> selectListByConversationId(Long conversationId, Long userId) {
        return selectList(new LambdaQueryWrapperX<AiGroupChatMemberDO>()
                .eq(AiGroupChatMemberDO::getConversationId, conversationId)
                .eq(AiGroupChatMemberDO::getUserId, userId)
                .orderByAsc(AiGroupChatMemberDO::getSortOrder));
    }

    /**
     * 跨租户查询公开群聊的候选成员，并按配置顺序返回。
     *
     * @param conversationId 群聊会话编号
     * @return 有序成员列表
     */
    default List<AiGroupChatMemberDO> selectListByConversationId(Long conversationId) {
        return selectList(new LambdaQueryWrapperX<AiGroupChatMemberDO>()
                .eq(AiGroupChatMemberDO::getConversationId, conversationId)
                .orderByAsc(AiGroupChatMemberDO::getSortOrder));
    }

    /**
     * 删除群聊全部成员，用于同一事务内重建成员配置或删除会话。
     *
     * @param conversationId 群聊会话编号
     * @param userId 登录用户编号
     */
    default void deleteByConversationId(Long conversationId, Long userId) {
        delete(new LambdaQueryWrapperX<AiGroupChatMemberDO>()
                .eq(AiGroupChatMemberDO::getConversationId, conversationId)
                .eq(AiGroupChatMemberDO::getUserId, userId));
    }
}
