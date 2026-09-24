package io.github.yoyocw.aichatkit.module.ai.contract.context;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 业务上下文请求，不依赖 Servlet、宿主安全对象或 ORM。 */
public final class AiBusinessContextRequest {
    /** 与本轮其他端口共用的身份及关联编号。 */
    private final AiInvocationContext context;
    /** 当前问题正文，沿用入口长度校验。 */
    private final String question;
    /** 服务端选择的能力集合，不是任意工具调用指令。 */
    private final Set<String> capabilities;

    public AiBusinessContextRequest(AiInvocationContext context, String question, Set<String> capabilities) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.question = question;
        this.capabilities = Collections.unmodifiableSet(new HashSet<String>(capabilities));
    }
    public String getNamespace() { return context.getNamespace(); }
    public String getTenantId() { return context.getTenantId(); }
    public String getActorId() { return context.getActorId(); }
    public String getInvocationId() { return context.getInvocationId(); }
    public String getQuestion() { return question; }
    public Set<String> getCapabilities() { return capabilities; }
}
