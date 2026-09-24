package io.github.yoyocw.aichatkit.module.ai.dal.mysql.origin;

import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.origin.AiMessageOriginDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 插入及读取不可变来源，不提供覆盖、更新、删除或软删除接口。
 * 当前无 MyBatis-Plus 表元数据，租户边界由适配层显式校验并写入 tenant_id 保证；
 * 查询显式限定租户，不能假定自动租户过滤。
 */
@Mapper
public interface AiMessageOriginMapper {
    /**
     * 同事务写入一次来源；主键冲突必须抛出，不得 upsert。
     * @param origin 已核对租户、用户和助手占位的来源
     * @return 插入行数，必须为 1
     */
    @Insert("INSERT INTO ai_message_origin (tenant_id, user_id, mode, message_id, client_record_id, "
            + "client_id, business_system, environment, app_id) VALUES (#{tenantId}, #{userId}, #{mode}, "
            + "#{messageId}, #{clientRecordId}, #{clientId}, #{businessSystem}, #{environment}, #{appId})")
    int insert(AiMessageOriginDO origin);

    /**
     * 按来源复合主键读取，不使用当前客户端记录重建历史来源。
     * @param tenantId 经适配层与可信上下文核对的租户编号
     * @param mode 服务端固定的 single/group 模式
     * @param messageId 正数助手消息编号
     * @return 不可变原来源；不存在时返回 null
     */
    @Select("SELECT tenant_id, user_id, mode, message_id, client_record_id, client_id, "
            + "business_system, environment, app_id FROM ai_message_origin "
            + "WHERE tenant_id = #{tenantId} AND mode = #{mode} AND message_id = #{messageId}")
    @ConstructorArgs({
            @Arg(column = "tenant_id", javaType = Long.class),
            @Arg(column = "user_id", javaType = Long.class),
            @Arg(column = "mode", javaType = String.class),
            @Arg(column = "message_id", javaType = Long.class),
            @Arg(column = "client_record_id", javaType = Long.class),
            @Arg(column = "client_id", javaType = String.class),
            @Arg(column = "business_system", javaType = String.class),
            @Arg(column = "environment", javaType = String.class),
            @Arg(column = "app_id", javaType = String.class)
    })
    AiMessageOriginDO selectOrigin(@Param("tenantId") Long tenantId, @Param("mode") String mode,
                                   @Param("messageId") Long messageId);
}
