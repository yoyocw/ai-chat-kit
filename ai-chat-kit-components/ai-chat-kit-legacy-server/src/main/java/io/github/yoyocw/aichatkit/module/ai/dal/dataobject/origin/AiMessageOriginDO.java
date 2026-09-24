package io.github.yoyocw.aichatkit.module.ai.dal.dataobject.origin;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 只插入的授权来源，不继承软删除基类，禁止由审计清理或消息删除重建。 */
@Getter
@RequiredArgsConstructor
public final class AiMessageOriginDO {
    /** 来源租户，与当前事务租户一致。 */
    private final Long tenantId;
    /** 本轮发起用户编号。 */
    private final Long userId;
    /** 模式 single/group，限定消息表。 */
    private final String mode;
    /** 本轮助手占位编号，与租户及模式共同唯一。 */
    private final Long messageId;
    /** 认证侧当前客户端实体主键，不是令牌代次。 */
    private final Long clientRecordId;
    /** 认证侧客户端字符串标识。 */
    private final String clientId;
    /** 认证侧绑定的业务系统。 */
    private final String businessSystem;
    /** 认证侧绑定的部署环境。 */
    private final String environment;
    /** 本轮实际调用且已授权的应用编号。 */
    private final String appId;
}
