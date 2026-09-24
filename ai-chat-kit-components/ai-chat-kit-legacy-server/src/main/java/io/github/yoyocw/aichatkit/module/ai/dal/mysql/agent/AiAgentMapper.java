package io.github.yoyocw.aichatkit.module.ai.dal.mysql.agent;

import io.github.yoyocw.aichatkit.compat.framework.common.enums.CommonStatusEnum;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.agent.AiAgentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Set;

/**
 * AI 智能体目录数据访问接口，提供全局候选目录和编码批量查询能力。
 */
@Mapper
public interface AiAgentMapper extends BaseMapperX<AiAgentDO> {

    /**
     * 查询全部启用智能体并按页面顺序返回。
     *
     * @return 启用智能体有序列表
     */
    default List<AiAgentDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<AiAgentDO>()
                .eq(AiAgentDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(AiAgentDO::getSort)
                .orderByAsc(AiAgentDO::getId));
    }

    /**
     * 查询指定业务编码对应的目录记录，包括已停用记录。
     *
     * @param codes 智能体业务编码，调用方必须保证非空
     * @return 按目录顺序排列的智能体列表
     */
    default List<AiAgentDO> selectListByCodes(List<String> codes) {
        return selectList(new LambdaQueryWrapperX<AiAgentDO>()
                .in(AiAgentDO::getCode, codes)
                .orderByAsc(AiAgentDO::getSort)
                .orderByAsc(AiAgentDO::getId));
    }

    /**
     * 查询指定业务编码中当前可执行的智能体。
     *
     * @param codes 去重并规范化后的智能体编码
     * @return 按目录顺序排列的启用智能体列表
     */
    default List<AiAgentDO> selectEnabledListByCodes(Set<String> codes) {
        return selectList(new LambdaQueryWrapperX<AiAgentDO>()
                .in(AiAgentDO::getCode, codes)
                .eq(AiAgentDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(AiAgentDO::getSort)
                .orderByAsc(AiAgentDO::getId));
    }
}
