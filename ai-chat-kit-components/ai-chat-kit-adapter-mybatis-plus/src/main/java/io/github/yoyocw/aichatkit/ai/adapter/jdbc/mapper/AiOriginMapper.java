package io.github.yoyocw.aichatkit.ai.adapter.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity.AiOriginEntity;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcScope;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 来源插入与助手占位归属、生成态校验必须是同一条语句。 */
public interface AiOriginMapper extends BaseMapper<AiOriginEntity> {
    @Insert("INSERT INTO ai_runtime_origin(namespace,tenant_id,actor_id,mode,message_id,client_record_id,"
            + "client_id,business_system,environment,app_id) SELECT namespace,tenant_id,actor_id,mode,id,"
            + "#{origin.clientRecordIdentifier},#{origin.clientId},#{origin.businessSystem},#{origin.environment},#{appId}"
            + " FROM ai_runtime_message WHERE namespace=#{scope.namespace} AND tenant_id=#{scope.tenantId}"
            + " AND actor_id=#{scope.actorId} AND deleted=false AND mode=#{mode} AND id=#{messageId}"
            + " AND role='assistant' AND status=0")
    int record(@Param("scope") AiJdbcScope scope, @Param("mode") String mode, @Param("messageId") Long messageId,
            @Param("origin") AiCallerOrigin origin, @Param("appId") String appId);
}
