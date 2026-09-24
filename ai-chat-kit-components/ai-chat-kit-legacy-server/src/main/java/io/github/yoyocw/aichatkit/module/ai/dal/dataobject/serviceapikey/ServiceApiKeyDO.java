package io.github.yoyocw.aichatkit.module.ai.dal.dataobject.serviceapikey;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MCP 第三方服务密钥持久化对象，每条记录代表一个独立平台或应用调用方。
 */
@TableName("ai_service_api_key")
@KeySequence("ai_service_api_key_seq")
@TenantIgnore
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceApiKeyDO extends TenantBaseDO {

    /** 调用方数据库主键编号。 */
    @TableId
    private Long id;

    /** 租户内唯一的稳定调用方编码，仅允许小写字母、数字、短横线和下划线。 */
    private String clientCode;

    /** 调用方显示名称，例如“百炼林业智能体”。 */
    private String clientName;

    /** 当前完整 MCP 密钥的 SHA-256 小写十六进制摘要，固定 64 字符。 */
    private String activeKeySha256;

    /** 轮换窗口内上一把 MCP 密钥摘要；清除后旧密钥立即失效。 */
    private String previousKeySha256;

    /** 绑定的后台只读服务账号用户编号。 */
    private Long serviceUserId;

    /** 单次分页最大返回条数，取值范围 1 至 200。 */
    private Integer maxPageSize;

    /** 调用方整体失效时间；为空表示不设置自动失效时间。 */
    private LocalDateTime expireTime;

    /** 启停状态，0 表示启用，1 表示停用。 */
    private Integer status;
}
