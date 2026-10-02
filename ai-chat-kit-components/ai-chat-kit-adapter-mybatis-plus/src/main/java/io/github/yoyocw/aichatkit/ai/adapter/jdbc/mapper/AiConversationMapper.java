package io.github.yoyocw.aichatkit.ai.adapter.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity.AiConversationEntity;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal.AiJdbcScope;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 普通 CRUD 使用 BaseMapper；分享租约由数据库时钟生成并返回。 */
public interface AiConversationMapper extends BaseMapper<AiConversationEntity> {
    @Select(value = "UPDATE ai_runtime_conversation SET share_code=#{candidate},share_status=1,"
            + "share_expire_at=clock_timestamp()+(#{days} * INTERVAL '1 day'),share_access_count=0,"
            + "share_last_access_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE namespace=#{scope.namespace}"
            + " AND tenant_id=#{scope.tenantId} AND actor_id=#{scope.actorId} AND deleted=false"
            + " AND mode=#{mode} AND id=#{id} RETURNING share_code,share_expire_at", affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE, useCache = false)
    AiConversationEntity issueShare(@Param("scope") AiJdbcScope scope, @Param("mode") String mode,
            @Param("id") Long id, @Param("days") int days, @Param("candidate") String candidate);
}
