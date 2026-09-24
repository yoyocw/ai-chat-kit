package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 内部数据库隔离参数；不执行认证，调用入口必须先捕获可信身份。 */
public final class AiJdbcScope {
    /** 本轮可信宿主身份，仅用于绑定查询参数，不作为认证证明。 */
    private final AiInvocationContext context;

    /** @param context 已认证上下文 @param namespace 本部署固定存储命名空间 */
    public AiJdbcScope(AiInvocationContext context, String namespace) {
        if (context == null || !valid(namespace) || !namespace.equals(context.getNamespace())
                || !valid(context.getTenantId()) || !valid(context.getActorId())
                || context.getInvocationId() == null || context.getInvocationId().trim().isEmpty()) {
            throw new IllegalStateException("AI 存储身份作用域无效");
        }
        this.context = context;
    }

    /** @return 有效不透明标识，保持原值，不做数字解析或大小写归一化 */
    private static boolean valid(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 128;
    }

    /** @return 固定顺序 namespace/tenant/actor，用于所有数据库查询 */
    public Object[] args(Object... rest) {
        Object[] result = new Object[rest.length + 3];
        result[0] = context.getNamespace(); result[1] = context.getTenantId(); result[2] = context.getActorId();
        System.arraycopy(rest, 0, result, 3, rest.length);
        return result;
    }
}
