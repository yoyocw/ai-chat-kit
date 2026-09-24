package io.github.yoyocw.aichatkit.module.ai.dal.mysql.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.PageResult;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.serviceapikey.vo.ServiceApiKeyPageReqVO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.serviceapikey.ServiceApiKeyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MCP 第三方服务密钥数据访问接口，提供租户管理查询和认证前全局摘要查询。
 */
@Mapper
public interface ServiceApiKeyMapper extends BaseMapperX<ServiceApiKeyDO> {

    /**
     * 查询当前租户的调用方分页数据。
     *
     * @param request 分页和筛选参数
     * @param tenantId 当前管理员租户编号
     * @return 当前租户调用方分页结果
     */
    default PageResult<ServiceApiKeyDO> selectPage(ServiceApiKeyPageReqVO request, Long tenantId) {
        return selectPage(request, new LambdaQueryWrapperX<ServiceApiKeyDO>()
                .eq(ServiceApiKeyDO::getTenantId, tenantId)
                .likeIfPresent(ServiceApiKeyDO::getClientCode, request.getClientCode())
                .likeIfPresent(ServiceApiKeyDO::getClientName, request.getClientName())
                .eqIfPresent(ServiceApiKeyDO::getStatus, request.getStatus())
                .orderByDesc(ServiceApiKeyDO::getId));
    }

    /**
     * 按当前或上一摘要查询调用方，包括停用和过期记录，状态由服务层统一判断。
     *
     * @param keySha256 完整 MCP 密钥的 SHA-256 摘要
     * @return 匹配的调用方，不存在时返回 null
     */
    default ServiceApiKeyDO selectByKeySha256(String keySha256) {
        return selectOne(new LambdaQueryWrapperX<ServiceApiKeyDO>()
                .and(wrapper -> wrapper.eq(ServiceApiKeyDO::getActiveKeySha256, keySha256)
                        .or().eq(ServiceApiKeyDO::getPreviousKeySha256, keySha256)));
    }

    /**
     * 统计任一摘要槽位是否已使用指定摘要，用于防止人工导入或程序错误造成重复。
     *
     * @param keySha256 待检查摘要
     * @return 使用该摘要的未删除记录数量
     */
    default Long selectCountByAnyKeySha256(String keySha256) {
        return selectCount(new LambdaQueryWrapperX<ServiceApiKeyDO>()
                .and(wrapper -> wrapper.eq(ServiceApiKeyDO::getActiveKeySha256, keySha256)
                        .or().eq(ServiceApiKeyDO::getPreviousKeySha256, keySha256)));
    }

    /**
     * 查询当前租户指定调用方编码的记录。
     *
     * @param tenantId 当前租户编号
     * @param clientCode 调用方稳定编码
     * @return 匹配记录，不存在时返回 null
     */
    default ServiceApiKeyDO selectByTenantIdAndClientCode(Long tenantId, String clientCode) {
        return selectOne(new LambdaQueryWrapperX<ServiceApiKeyDO>()
                .eq(ServiceApiKeyDO::getTenantId, tenantId)
                .eq(ServiceApiKeyDO::getClientCode, clientCode));
    }

    /**
     * 查询当前租户指定主键记录，防止管理接口跨租户访问。
     *
     * @param tenantId 当前租户编号
     * @param id 调用方主键编号
     * @return 匹配记录，不存在时返回 null
     */
    default ServiceApiKeyDO selectByTenantIdAndId(Long tenantId, Long id) {
        return selectOne(new LambdaQueryWrapperX<ServiceApiKeyDO>()
                .eq(ServiceApiKeyDO::getTenantId, tenantId)
                .eq(ServiceApiKeyDO::getId, id));
    }

    /**
     * 按主键锁定未删除记录，串行化密钥轮换和上一密钥清理。
     *
     * @param id 调用方主键编号
     * @return 已加行锁的调用方记录，不存在时返回 null
     */
    @Select("SELECT * FROM ai_service_api_key WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ServiceApiKeyDO selectByIdForUpdate(Long id);

    /**
     * 将上一密钥摘要更新为 NULL，使旧密钥立即失效并避免空字符串唯一索引冲突。
     *
     * @param id 调用方主键编号
     * @return 实际更新行数
     */
    @Update("UPDATE ai_service_api_key SET previous_key_sha256 = NULL, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int clearPreviousKey(Long id);
}
