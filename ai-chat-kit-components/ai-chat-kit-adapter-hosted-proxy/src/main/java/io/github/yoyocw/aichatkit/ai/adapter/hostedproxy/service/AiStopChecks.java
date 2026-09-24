package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.*;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import java.util.Objects;

/** 停止协调内部严格校验，不把原始异常、票据、身份或配置值带到日志。 */
final class AiStopChecks {
    /** 协议固定最大授权窗口，毫秒。 */
    static final long MAX_TTL = 30000;
    private AiStopChecks() { }

    /** 不透明标识原样保留；拒绝空值及过长值。 */
    static String text(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 128) { throw denied(); }
        return value;
    }

    /** 明确有效截止时间，不用宽限值延长。 */
    static long future(long value) {
        if (value <= System.currentTimeMillis()) { throw denied(); }
        return value;
    }

    /** 固定单/群聊模式及正数助手目标。 */
    static void target(AiChatMode mode, Long id) {
        if ((mode != AiChatMode.SINGLE && mode != AiChatMode.GROUP) || id == null || id <= 0) { throw denied(); }
    }

    /** 用户必须属于部署命名空间及接收方租户。 */
    static void user(AiHostSession user, AiStopReceiver receiver) {
        if (user == null || !receiver.getNamespace().equals(user.getNamespace())
                || !receiver.getTenantId().equals(user.getTenantId())) { throw denied(); }
        text(user.getActorId()); text(user.getSessionId()); future(user.getExpiresAtMillis());
    }

    /** 比对原用户四个关联标识；刷新或另一有效会话不能替换原票据会话。 */
    static void sameUser(AiHostSession expected, AiHostSession actual, AiStopReceiver receiver) {
        user(expected, receiver); user(actual, receiver);
        if (!expected.getActorId().equals(actual.getActorId()) || !expected.getSessionId().equals(actual.getSessionId())) {
            throw denied();
        }
    }

    /** 原机器和消费者都必须匹配真实实体；原机器必须有当前有效的系统、环境及应用绑定。 */
    static void machine(AiStopMachineIdentity identity, AiStopReceiver receiver, AiStopMachineRole role) {
        if (identity == null || role == null || !receiver.getNamespace().equals(identity.getNamespace())
                || !receiver.getTenantId().equals(identity.getTenantId())) { throw denied(); }
        boolean original = role == AiStopMachineRole.ORIGINAL;
        if (!(original ? receiver.getOriginalClientId() : receiver.getConsumerClientId()).equals(identity.getClientId())
                || !(original ? receiver.getOriginalClientRecordId() : receiver.getConsumerClientRecordId())
                .equals(identity.getClientRecordId())) { throw denied(); }
        text(identity.getSessionId()); future(identity.getExpiresAtMillis());
        if (original && (!receiver.getBusinessSystem().equals(identity.getBusinessSystem())
                || !receiver.getEnvironment().equals(identity.getEnvironment())
                || identity.getAllowedAppIds().isEmpty() || identity.getAllowedAppIds().size() > 32)) { throw denied(); }
        for (String app : identity.getAllowedAppIds()) {
            if ("*".equals(text(app))) { throw denied(); }
        }
        if (!original && !identity.getAllowedAppIds().isEmpty()) { throw denied(); }
    }

    /** 复查原机器session和当前应用集合，不因再获取凭据或新增权限扩大原票据。 */
    static void sameMachine(AiStopMachineIdentity before, AiStopMachineIdentity after,
                            AiStopReceiver receiver, AiStopMachineRole role) {
        machine(before, receiver, role); machine(after, receiver, role);
        if (!before.getSessionId().equals(after.getSessionId())
                || !before.getAllowedAppIds().equals(after.getAllowedAppIds())
                || !Objects.equals(before.getBusinessSystem(), after.getBusinessSystem())
                || !Objects.equals(before.getEnvironment(), after.getEnvironment())) { throw denied(); }
    }

    /** 验证实际作用域重新捕获的上下文，不把参数冒充登录身份。 */
    static void context(AiInvocationContext context, AiHostSession user) {
        if (context == null || !user.getNamespace().equals(context.getNamespace())
                || !user.getTenantId().equals(context.getTenantId())
                || !user.getActorId().equals(context.getActorId())) { throw denied(); }
        text(context.getInvocationId()); future(user.getExpiresAtMillis());
    }

    /** 固定无cause异常，禁止回显凭据及源响应。 */
    static IllegalStateException denied() { return new IllegalStateException("当前无法完成 v2 授权停止"); }
}
