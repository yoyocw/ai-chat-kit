package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** 部署级停止接收方绑定；不包含客户端秘密，修改后重启生效。 */
@Getter
@Setter
@EqualsAndHashCode
public class AiHostedStopReceiver {
    /** 部署内唯一 audience，1至64个字母、数字、下划线或横线。 */
    private String audience;
    /** 原调用方与消费者会话共同所属租户，允许零。 */
    private Long tenantId;
    /** 原调用方绑定的业务系统标识。 */
    private String businessSystem;
    /** 原调用方绑定的环境标识。 */
    private String environment;
    /** 消费者 OAuth 客户端必须获准的目标资源标识。 */
    private String resourceId;
    /** 原业务客户端字符串标识。 */
    private String originalClientId;
    /** 原业务客户端当前实体主键，必须为正。 */
    private Long originalClientRecordId;
    /** 独立 AI 消费客户端字符串标识，不得与原业务客户端相同。 */
    private String consumerClientId;
    /** 独立 AI 消费客户端实体主键，必须为正且不同于原业务客户端。 */
    private Long consumerClientRecordId;
}
