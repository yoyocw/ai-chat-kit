package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostExecutionScopePort;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;

/** 林业异步租户作用域；不构造 LoginUser，也不改变 SecurityContext。 */
@Component
@RequiredArgsConstructor
public class PlatformExecutionScopeAdapter implements AiHostExecutionScopePort {
    /** 状态监测使用已经核验与林业 MyBatis 同源的事务绑定。 */
    private final AiTransactionExecutor transactions;

    /**
     * 先建立租户作用域再开启事务，避免动态数据源路由读取原线程租户。
     * 两秒事务超时下传给MyBatis/JDBC；连接池获取与网络超时仍由部署配置控制。
     */
    @Override
    public void executeStateRead(AiInvocationContext context, Runnable task) {
        if (task == null) {
            throw new IllegalStateException("缺少生成状态查询");
        }
        execute(context, () -> {
            transactions.requiresNewReadOnly(() -> {
                task.run();
                return null;
            }, 2);
        });
    }

    @Override
    public void execute(AiInvocationContext context, Runnable task) {
        // 防御校验内部错误调用；正常路径在同步 capture 时已经保证 Long 映射。
        if (context == null || !"platform".equals(context.getNamespace()) || task == null
                || context.getActorId() == null || context.getActorId().trim().isEmpty()
                || context.getInvocationId() == null || context.getInvocationId().trim().isEmpty()) {
            throw new IllegalStateException("无效宿主执行上下文");
        }
        Long tenantId;
        try {
            tenantId = Long.valueOf(context.getTenantId());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("无效宿主租户标识");
        }
        Long oldTenantId = TenantContextHolder.getTenantId();
        boolean oldIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(false);
            task.run();
        } finally {
            TenantContextHolder.setTenantId(oldTenantId);
            TenantContextHolder.setIgnore(oldIgnore);
        }
    }
}
