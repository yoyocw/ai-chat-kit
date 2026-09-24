package io.github.yoyocw.aichatkit.module.ai.dal.dataobject.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 第三方调用方与稳定端点编码的授权关系持久化对象。
 */
@TableName("ai_service_api_key_endpoint")
@KeySequence("ai_service_api_key_endpoint_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceApiKeyEndpointDO extends TenantBaseDO {

    /** 授权关系主键编号。 */
    @TableId
    private Long id;

    /** 所属 MCP 第三方调用方主键编号，必须指向同租户未删除调用方。 */
    private Long serviceApiKeyId;

    /** 代码注解声明的全局稳定 MCP 端点编码，长度不超过 128 个字符。 */
    private String endpointCode;
}
