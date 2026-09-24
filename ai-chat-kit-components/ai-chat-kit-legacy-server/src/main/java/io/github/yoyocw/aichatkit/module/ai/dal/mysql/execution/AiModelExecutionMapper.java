package io.github.yoyocw.aichatkit.module.ai.dal.mysql.execution;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.execution.AiModelExecutionDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_FAILED;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.STATUS_GENERATING;

/**
 * AI 模型执行审计数据访问接口，提供请求级审计创建、指标终态更新和遗留执行收口能力。
 */
@Mapper
public interface AiModelExecutionMapper extends BaseMapperX<AiModelExecutionDO> {

    /**
     * 按执行模式和占位消息编号条件更新仍处于执行中的审计记录。
     *
     * @param userId 已验证的当前用户编号，限定记录归属
     * @param execution 本轮需要更新的指标与终态
     * @return 是否成功将执行中记录更新为目标终态
     */
    default boolean updateRunning(Long userId, AiModelExecutionDO execution) {
        return update(execution, new LambdaUpdateWrapper<AiModelExecutionDO>()
                .eq(AiModelExecutionDO::getUserId, userId)
                .eq(AiModelExecutionDO::getMode, execution.getMode())
                .eq(AiModelExecutionDO::getMessageId, execution.getMessageId())
                .eq(AiModelExecutionDO::getStatus, STATUS_GENERATING)) > 0;
    }

    /**
     * 将指定会话中超过时间边界的执行中审计收口为失败。
     *
     * @param userId 已验证的当前用户编号，限定记录归属
     * @param mode 执行模式
     * @param conversationId 业务会话编号
     * @param expiredBefore 创建时间边界，早于该时间的执行视为遗留任务
     * @param errorCode 稳定超时错误编码
     * @return 实际收口的审计记录数量
     */
    default int failStale(Long userId, String mode, Long conversationId, LocalDateTime expiredBefore, String errorCode) {
        AiModelExecutionDO update = AiModelExecutionDO.builder().status(STATUS_FAILED).errorCode(errorCode).build();
        return update(update, new LambdaUpdateWrapper<AiModelExecutionDO>()
                .eq(AiModelExecutionDO::getUserId, userId)
                .eq(AiModelExecutionDO::getMode, mode)
                .eq(AiModelExecutionDO::getConversationId, conversationId)
                .eq(AiModelExecutionDO::getStatus, STATUS_GENERATING)
                .lt(AiModelExecutionDO::getCreateTime, expiredBefore));
    }

    /**
     * 原子增加一次短期会话失效重试计数。
     *
     * @param userId 已验证的当前用户编号，限定记录归属
     * @param mode 执行模式
     * @param messageId 本轮助手占位消息编号
     * @return 是否更新到仍在执行的审计记录
     */
    default boolean incrementRetry(Long userId, String mode, Long messageId) {
        return update(null, new LambdaUpdateWrapper<AiModelExecutionDO>()
                .eq(AiModelExecutionDO::getUserId, userId)
                .setSql("retry_count = retry_count + 1")
                .eq(AiModelExecutionDO::getMode, mode)
                .eq(AiModelExecutionDO::getMessageId, messageId)
                .eq(AiModelExecutionDO::getStatus, STATUS_GENERATING)) > 0;
    }

    /**
     * 查询仍处于执行中的请求级审计记录。
     *
     * @param userId 已验证的当前用户编号，限定记录归属
     * @param mode 执行模式
     * @param messageId 助手占位消息编号
     * @return 执行中审计记录，不存在或已进入终态时返回 null
     */
    default AiModelExecutionDO selectRunning(Long userId, String mode, Long messageId) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModelExecutionDO>()
                .eq(AiModelExecutionDO::getUserId, userId)
                .eq(AiModelExecutionDO::getMode, mode)
                .eq(AiModelExecutionDO::getMessageId, messageId)
                .eq(AiModelExecutionDO::getStatus, STATUS_GENERATING));
    }
}
