package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity.AiScopedEntity;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 可信身份作用域；每次创建新的 Wrapper，不能跨请求复用可变查询条件。 */
public final class AiJdbcScope {
    private final String namespace;
    private final String tenantId;
    private final String actorId;

    public AiJdbcScope(AiInvocationContext context, String namespace) {
        if (context == null || !valid(namespace) || !namespace.equals(context.getNamespace())
                || !valid(context.getTenantId()) || !valid(context.getActorId())
                || context.getInvocationId() == null || context.getInvocationId().trim().isEmpty()) {
            throw new IllegalStateException("AI 存储身份作用域无效");
        }
        this.namespace = namespace; this.tenantId = context.getTenantId(); this.actorId = context.getActorId();
    }

    /** 仅供分享记录命中后使用数据库保存的归属，不构造访客登录身份。 */
    AiJdbcScope(String namespace, String tenantId, String actorId) {
        if (!valid(namespace) || !valid(tenantId) || !valid(actorId)) {
            throw new IllegalStateException("AI 分享记录归属无效");
        }
        this.namespace = namespace; this.tenantId = tenantId; this.actorId = actorId;
    }

    public String getNamespace() { return namespace; }
    public String getTenantId() { return tenantId; }
    public String getActorId() { return actorId; }

    public <T> QueryWrapper<T> query(String mode) {
        checkMode(mode);
        return new QueryWrapper<T>().eq("namespace", namespace).eq("tenant_id", tenantId)
                .eq("actor_id", actorId).eq("mode", mode).eq("deleted", false);
    }

    public <T> UpdateWrapper<T> update(String mode) {
        checkMode(mode);
        return new UpdateWrapper<T>().eq("namespace", namespace).eq("tenant_id", tenantId)
                .eq("actor_id", actorId).eq("mode", mode).eq("deleted", false);
    }

    public <T extends AiScopedEntity> T initialize(T entity, String mode) {
        checkMode(mode);
        entity.setNamespace(namespace); entity.setTenantId(tenantId); entity.setActorId(actorId); entity.setMode(mode);
        return entity;
    }

    private static void checkMode(String mode) {
        if (!"single".equals(mode) && !"group".equals(mode)) { throw new IllegalArgumentException("AI 执行模式无效"); }
    }
    private static boolean valid(String value) { return value != null && !value.trim().isEmpty() && value.length() <= 128; }
}
