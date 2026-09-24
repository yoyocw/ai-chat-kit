package io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;
import java.util.List;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiShareConstants.STATUS_ACTIVE;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiShareConstants.STATUS_DISABLED;

/**
 * AI 对话会话数据访问接口，提供按登录用户隔离的会话查询能力。
 */
@Mapper
public interface AiChatConversationMapper extends BaseMapperX<AiChatConversationDO> {

    /**
     * 查询用户拥有的指定会话。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @return 匹配的会话，不存在或不属于该用户时返回 null
     */
    default AiChatConversationDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(AiChatConversationDO::getId, id, AiChatConversationDO::getUserId, userId);
    }

    /**
     * 锁定用户拥有的会话，串行化发送、重命名和删除，避免产生逻辑孤儿消息。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @return 已锁定会话，不存在或不属于该用户时返回 null
     */
    default AiChatConversationDO selectByIdAndUserIdForUpdate(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<AiChatConversationDO>()
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getUserId, userId)
                .last("FOR UPDATE"));
    }

    /**
     * 清除已被百炼判定失效的短期会话标识，避免后续请求持续复用坏会话。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @param turnId 当前助手消息编号，旧调用不能清除新一轮会话
     */
    default void clearBailianSession(Long id, Long userId, Long turnId) {
        update(null, new LambdaUpdateWrapper<AiChatConversationDO>()
                .set(AiChatConversationDO::getBailianSessionId, null)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getBailianTurnId, turnId)
                .eq(AiChatConversationDO::getUserId, userId));
    }

    /**
     * 在发送事务持有会话行锁时绑定本轮应用与消息。
     * @param id 会话编号
     * @param userId 会话所有者
     * @param appId 本轮应用快照
     * @param turnId 本轮助手消息编号
     * @param reset 应用改变或来源未知时清除旧会话
     */
    default void bindBailianTurn(Long id, Long userId, String appId, Long turnId, boolean reset) {
        update(null, new LambdaUpdateWrapper<AiChatConversationDO>()
                .set(AiChatConversationDO::getBailianAppId, appId)
                .set(AiChatConversationDO::getBailianTurnId, turnId)
                .set(reset, AiChatConversationDO::getBailianSessionId, null)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getUserId, userId));
    }

    /**
     * 仅允许当前应用的当前轮次保存远端会话，迟到结果不会覆盖新调用的绑定。
     * @param id 会话编号
     * @param userId 会话所有者
     * @param appId 实际调用应用
     * @param turnId 本轮助手消息编号
     * @param sessionId 百炼返回的会话标识
     */
    default void saveBailianSession(Long id, Long userId, String appId, Long turnId, String sessionId) {
        update(null, new LambdaUpdateWrapper<AiChatConversationDO>()
                .set(AiChatConversationDO::getBailianSessionId, sessionId)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getUserId, userId)
                .eq(AiChatConversationDO::getBailianAppId, appId)
                .eq(AiChatConversationDO::getBailianTurnId, turnId));
    }

    /**
     * 更新当前用户会话的置顶状态，并同步维护置顶排序时间。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @param pinned 是否置顶
     * @param pinnedTime 置顶时间，取消置顶时为空
     */
    default void updatePin(Long id, Long userId, boolean pinned, java.time.LocalDateTime pinnedTime) {
        update(null, new LambdaUpdateWrapper<AiChatConversationDO>()
                .set(AiChatConversationDO::getPinned, pinned)
                .set(AiChatConversationDO::getPinnedTime, pinnedTime)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getUserId, userId));
    }

    /**
     * 启用当前用户会话分享，并为重新生成的分享重置访问统计。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @param shareCode 新生成的不可枚举分享码
     * @param expireTime 分享过期时间，必须晚于当前时间
     * @return 更新记录数；会话不存在或不属于当前用户时为 0
     */
    default int activateShare(Long id, Long userId, String shareCode, LocalDateTime expireTime) {
        return update(null, new LambdaUpdateWrapper<AiChatConversationDO>()
                .set(AiChatConversationDO::getShareCode, shareCode)
                .set(AiChatConversationDO::getShareStatus, STATUS_ACTIVE)
                .set(AiChatConversationDO::getShareExpireTime, expireTime)
                .set(AiChatConversationDO::getShareAccessCount, 0L)
                .set(AiChatConversationDO::getShareLastAccessTime, null)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getUserId, userId));
    }

    /**
     * 关闭当前用户会话分享并清除公开入口，历史访问统计保留用于审计。
     *
     * @param id 会话编号
     * @param userId 登录用户编号
     * @return 更新记录数；会话不存在或不属于当前用户时为 0
     */
    default int disableShare(Long id, Long userId) {
        return update(null, new LambdaUpdateWrapper<AiChatConversationDO>()
                .set(AiChatConversationDO::getShareCode, null)
                .set(AiChatConversationDO::getShareStatus, STATUS_DISABLED)
                .set(AiChatConversationDO::getShareExpireTime, null)
                .eq(AiChatConversationDO::getId, id)
                .eq(AiChatConversationDO::getUserId, userId));
    }

    /**
     * 按全局唯一分享码查询启用且未过期会话；调用方必须在租户忽略上下文中执行。
     *
     * @param shareCode 不可枚举分享码
     * @param accessTime 本次访问判定时间
     * @return 分享会话，不存在、已取消或已过期时返回 null
     */
    default AiChatConversationDO selectActiveShare(String shareCode, LocalDateTime accessTime) {
        return selectOne(new LambdaQueryWrapperX<AiChatConversationDO>()
                .eq(AiChatConversationDO::getShareCode, shareCode)
                .eq(AiChatConversationDO::getShareStatus, STATUS_ACTIVE)
                .gt(AiChatConversationDO::getShareExpireTime, accessTime));
    }

    /**
     * 在分享仍启用且未过期时原子增加访问次数，防止并发覆盖计数。
     *
     * @param id 会话编号
     * @param shareCode 本次访问使用的分享码
     * @param accessTime 访问完成时间，同时写入最近访问时间
     * @return 更新记录数；分享在响应组装期间失效时为 0
     */
    @Update("UPDATE ai_chat_conversation "
            + "SET share_access_count = share_access_count + 1, share_last_access_time = #{accessTime} "
            + "WHERE id = #{id} AND share_code = #{shareCode} AND share_status = 1 "
            + "AND share_expire_time > #{accessTime} AND deleted = 0")
    int incrementShareAccess(@Param("id") Long id, @Param("shareCode") String shareCode,
                             @Param("accessTime") LocalDateTime accessTime);

    /**
     * 查询用户全部历史会话，置顶会话优先，再按置顶时间和最近更新时间倒序排列。
     *
     * @param userId 登录用户编号
     * @return 用户历史会话列表
     */
    default List<AiChatConversationDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<AiChatConversationDO>()
                .eq(AiChatConversationDO::getUserId, userId)
                .orderByDesc(AiChatConversationDO::getPinned)
                .orderByDesc(AiChatConversationDO::getPinnedTime)
                .orderByDesc(AiChatConversationDO::getUpdateTime));
    }
}
