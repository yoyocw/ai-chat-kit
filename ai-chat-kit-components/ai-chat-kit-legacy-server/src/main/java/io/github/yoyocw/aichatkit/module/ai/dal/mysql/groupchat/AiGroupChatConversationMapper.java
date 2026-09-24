package io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatConversationDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiShareConstants.STATUS_ACTIVE;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiShareConstants.STATUS_DISABLED;

/**
 * AI 群聊会话数据访问接口，提供用户隔离查询、行锁和短期会话清理能力。
 */
@Mapper
public interface AiGroupChatConversationMapper extends BaseMapperX<AiGroupChatConversationDO> {

    /**
     * 查询当前用户拥有的群聊会话。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     * @return 匹配会话，不存在或不属于当前用户时返回 null
     */
    default AiGroupChatConversationDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(AiGroupChatConversationDO::getId, id, AiGroupChatConversationDO::getUserId, userId);
    }

    /**
     * 锁定当前用户群聊会话，串行化成员调整、删除和消息发送。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     * @return 已锁定会话，不存在时返回 null
     */
    default AiGroupChatConversationDO selectByIdAndUserIdForUpdate(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<AiGroupChatConversationDO>()
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId)
                .last("FOR UPDATE"));
    }

    /**
     * 查询当前用户全部群聊，置顶会话优先并按置顶时间和最近更新时间倒序返回。
     *
     * @param userId 登录用户编号
     * @return 群聊会话列表
     */
    default List<AiGroupChatConversationDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<AiGroupChatConversationDO>()
                .eq(AiGroupChatConversationDO::getUserId, userId)
                .orderByDesc(AiGroupChatConversationDO::getPinned)
                .orderByDesc(AiGroupChatConversationDO::getPinnedTime)
                .orderByDesc(AiGroupChatConversationDO::getUpdateTime));
    }

    /**
     * 更新当前用户群聊的置顶状态。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     * @param pinned 目标置顶状态
     * @param pinnedTime 置顶时间，取消置顶时为空
     * @return 更新记录数
     */
    default int updatePin(Long id, Long userId, Boolean pinned, LocalDateTime pinnedTime) {
        return update(null, new LambdaUpdateWrapper<AiGroupChatConversationDO>()
                .set(AiGroupChatConversationDO::getPinned, pinned)
                .set(AiGroupChatConversationDO::getPinnedTime, pinnedTime)
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId));
    }

    /**
     * 启用当前用户群聊分享，并为重新生成的分享重置访问统计。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     * @param shareCode 新生成的不可枚举分享码
     * @param expireTime 分享过期时间，必须晚于当前时间
     * @return 更新记录数；群聊不存在或不属于当前用户时为 0
     */
    default int activateShare(Long id, Long userId, String shareCode, LocalDateTime expireTime) {
        return update(null, new LambdaUpdateWrapper<AiGroupChatConversationDO>()
                .set(AiGroupChatConversationDO::getShareCode, shareCode)
                .set(AiGroupChatConversationDO::getShareStatus, STATUS_ACTIVE)
                .set(AiGroupChatConversationDO::getShareExpireTime, expireTime)
                .set(AiGroupChatConversationDO::getShareAccessCount, 0L)
                .set(AiGroupChatConversationDO::getShareLastAccessTime, null)
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId));
    }

    /**
     * 关闭当前用户群聊分享并清除公开入口，历史访问统计保留用于审计。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     * @return 更新记录数；群聊不存在或不属于当前用户时为 0
     */
    default int disableShare(Long id, Long userId) {
        return update(null, new LambdaUpdateWrapper<AiGroupChatConversationDO>()
                .set(AiGroupChatConversationDO::getShareCode, null)
                .set(AiGroupChatConversationDO::getShareStatus, STATUS_DISABLED)
                .set(AiGroupChatConversationDO::getShareExpireTime, null)
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId));
    }

    /**
     * 根据公开分享码跨租户查询启用且未过期群聊；调用方必须显式忽略租户过滤。
     *
     * @param shareCode 不可枚举分享码
     * @param accessTime 本次访问判定时间
     * @return 有效群聊，不存在、已取消或已过期时返回 null
     */
    default AiGroupChatConversationDO selectActiveShare(String shareCode, LocalDateTime accessTime) {
        return selectOne(new LambdaQueryWrapperX<AiGroupChatConversationDO>()
                .eq(AiGroupChatConversationDO::getShareCode, shareCode)
                .eq(AiGroupChatConversationDO::getShareStatus, STATUS_ACTIVE)
                .gt(AiGroupChatConversationDO::getShareExpireTime, accessTime));
    }

    /**
     * 在分享仍启用且未过期时原子增加访问次数，防止并发覆盖计数。
     *
     * @param id 群聊会话编号
     * @param shareCode 本次访问使用的分享码
     * @param accessTime 访问完成时间，同时写入最近访问时间
     * @return 更新记录数；分享在响应组装期间失效时为 0
     */
    @Update("UPDATE ai_group_chat_conversation "
            + "SET share_access_count = share_access_count + 1, share_last_access_time = #{accessTime} "
            + "WHERE id = #{id} AND share_code = #{shareCode} AND share_status = 1 "
            + "AND share_expire_time > #{accessTime} AND deleted = 0")
    int incrementShareAccess(@Param("id") Long id, @Param("shareCode") String shareCode,
                             @Param("accessTime") LocalDateTime accessTime);

    /**
     * 清除已失效的百炼工作流短期会话标识。
     *
     * @param id 群聊会话编号
     * @param userId 登录用户编号
     */
    default void clearBailianSession(Long id, Long userId) {
        update(null, new LambdaUpdateWrapper<AiGroupChatConversationDO>()
                .set(AiGroupChatConversationDO::getBailianSessionId, null)
                .set(AiGroupChatConversationDO::getBailianTurnId, null)
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId));
    }

    /**
     * 仅清除当前轮次失效的会话，保留新轮次绑定。
     * @param id 会话编号
     * @param userId 会话所有者
     * @param turnId 发起调用的助手消息编号
     */
    default void clearBailianSession(Long id, Long userId, Long turnId) {
        update(null, new LambdaUpdateWrapper<AiGroupChatConversationDO>()
                .set(AiGroupChatConversationDO::getBailianSessionId, null)
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId)
                .eq(AiGroupChatConversationDO::getBailianTurnId, turnId));
    }

    /**
     * 持有会话行锁时绑定本轮应用和占位消息。
     * @param id 会话编号
     * @param userId 会话所有者
     * @param appId 本轮应用快照
     * @param turnId 本轮助手消息编号
     * @param reset 应用变化或来源未知时清除云端上下文
     */
    default void bindBailianTurn(Long id, Long userId, String appId, Long turnId, boolean reset) {
        update(null, new LambdaUpdateWrapper<AiGroupChatConversationDO>()
                .set(AiGroupChatConversationDO::getBailianAppId, appId)
                .set(AiGroupChatConversationDO::getBailianTurnId, turnId)
                .set(reset, AiGroupChatConversationDO::getBailianSessionId, null)
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId));
    }

    /**
     * 只保存当前应用当前轮次的远端会话，旧响应无法覆盖新调用。
     * @param id 会话编号
     * @param userId 会话所有者
     * @param appId 实际调用应用
     * @param turnId 本轮助手消息编号
     * @param sessionId 百炼返回的会话标识
     */
    default void saveBailianSession(Long id, Long userId, String appId, Long turnId, String sessionId) {
        update(null, new LambdaUpdateWrapper<AiGroupChatConversationDO>()
                .set(AiGroupChatConversationDO::getBailianSessionId, sessionId)
                .eq(AiGroupChatConversationDO::getId, id)
                .eq(AiGroupChatConversationDO::getUserId, userId)
                .eq(AiGroupChatConversationDO::getBailianAppId, appId)
                .eq(AiGroupChatConversationDO::getBailianTurnId, turnId));
    }
}
