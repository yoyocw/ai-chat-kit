package io.github.yoyocw.aichatkit.module.ai.dal.mysql.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.mapper.BaseMapperX;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.query.LambdaQueryWrapperX;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.serviceapikey.ServiceApiKeyEndpointDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Set;

/**
 * MCP 调用方端点授权数据访问接口，提供按调用方读取和差量同步授权的基础能力。
 */
@Mapper
public interface ServiceApiKeyEndpointMapper extends BaseMapperX<ServiceApiKeyEndpointDO> {

    /**
     * 查询指定调用方的全部未删除端点授权。
     *
     * @param serviceApiKeyId MCP 第三方调用方主键编号
     * @return 按端点编码排序的授权关系列表，不存在时返回空列表
     */
    default List<ServiceApiKeyEndpointDO> selectListByServiceApiKeyId(Long serviceApiKeyId) {
        return selectList(new LambdaQueryWrapperX<ServiceApiKeyEndpointDO>()
                .eq(ServiceApiKeyEndpointDO::getServiceApiKeyId, serviceApiKeyId)
                .orderByAsc(ServiceApiKeyEndpointDO::getEndpointCode));
    }

    /**
     * 逻辑删除指定租户调用方已取消的端点授权，未变化授权保留原关系主键。
     *
     * @param serviceApiKeyId MCP 第三方调用方主键编号
     * @param tenantId 调用方固定绑定租户编号
     * @param endpointCodes 本次取消授权的端点编码集合，调用方必须保证非空
     * @return 实际逻辑删除行数
     */
    default int deleteByServiceApiKeyIdAndEndpointCodes(Long serviceApiKeyId, Long tenantId,
                                                        Set<String> endpointCodes) {
        return delete(new LambdaQueryWrapperX<ServiceApiKeyEndpointDO>()
                .eq(ServiceApiKeyEndpointDO::getServiceApiKeyId, serviceApiKeyId)
                .eq(ServiceApiKeyEndpointDO::getTenantId, tenantId)
                .in(ServiceApiKeyEndpointDO::getEndpointCode, endpointCodes));
    }
}
