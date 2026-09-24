package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.*;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.config.AiHostedProxyStopProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiStopOriginPrecheckService;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 可选v2停止协调：两真实原身份、独立消费机器、不可变来源预检、一次消费及本地事务提交。
 * 不建立默认认证，不兼容v1/MCP工具凭据，不宣称Redis消费和数据库事务跨库原子。
 */
public final class AiHostedStopCoordinator {
    /** 完整固定接收方策略快照。 */
    private final AiStopReceiver receiver;
    /** 实际机器会话及当前绑定源。 */
    private final AiStopMachinePort machines;
    /** 实际用户停止证明和权限源。 */
    private final AiStopUserProofPort users;
    /** 经真实源核验后才使用的原用户短阶段作用域。 */
    private final AiStopScopePort scopes;
    /** 真实v2持久化存储。 */
    private final AiStopTicketStorePort tickets;
    /** 持有数据库锁的本地停止提交实现。 */
    private final AiConsumedStopPort commits;
    /** 独立只读事务中的原来源预检。 */
    private final AiStopOriginPrecheckService precheck;
    /** 局部协议编解码与随机票据生成，不保存原凭据。 */
    private final AiStopTicketCodec codec = new AiStopTicketCodec();

    /**
     * @param namespace 固定宿主namespace @param properties 显式启用的完整接收方策略
     * @param machines 真实机器源 @param users 真实用户证明源 @param scopes 原用户作用域
     * @param tickets 真实一次性存储 @param commits 本地锁/CAS实现 @param precheck 独立事务来源检查
     */
    public AiHostedStopCoordinator(String namespace, AiHostedProxyStopProperties properties,
            AiStopMachinePort machines, AiStopUserProofPort users, AiStopScopePort scopes,
            AiStopTicketStorePort tickets, AiConsumedStopPort commits, AiStopOriginPrecheckService precheck) {
        this.receiver = AiStopReceiverPolicy.select(namespace, properties);
        this.machines = Objects.requireNonNull(machines); this.users = Objects.requireNonNull(users);
        this.scopes = Objects.requireNonNull(scopes); this.tickets = Objects.requireNonNull(tickets);
        this.commits = Objects.requireNonNull(commits); this.precheck = Objects.requireNonNull(precheck);
    }

    /**
     * @param originalMachineBearer 真实原业务机器凭据 @param userProof 普通真实用户凭据，不能是MCP工具JWT
     * @param mode 服务端固定单/群聊停止 @param messageId 正数助手消息ID
     * @return 最长30秒且受原两会话期限约束的专用v2票据，不能输出到日志/URL/模型
     * @throws IllegalStateException 身份、权限、部署绑定、存储异常或处于数据库事务中
     */
    public String issue(String originalMachineBearer, String userProof, AiChatMode mode, Long messageId) {
        try {
            outsideTransaction(); AiStopChecks.target(mode, messageId);
            AiStopMachineIdentity machine = machines.authenticate(originalMachineBearer, receiver, AiStopMachineRole.ORIGINAL);
            AiStopChecks.machine(machine, receiver, AiStopMachineRole.ORIGINAL);
            AiHostSession user = users.verify(userProof, receiver, mode);
            AiStopChecks.user(user, receiver);
            long now = System.currentTimeMillis();
            long expiry = Math.min(now + AiStopChecks.MAX_TTL, Math.min(machine.getExpiresAtMillis(), user.getExpiresAtMillis()));
            AiStopChecks.future(expiry);
            AiStopTicket ticket = new AiStopTicket(2, receiver, user, machine, mode, messageId, now, expiry);
            String raw = codec.encode(ticket);
            for (int attempt = 0; attempt < 3; attempt++) {
                String credential = codec.credential();
                long ttl = ticket.getExpiresAtMillis() - System.currentTimeMillis();
                if (ttl <= 0) { throw AiStopChecks.denied(); }
                if (tickets.create(codec.digest(credential), raw, ttl)) { return credential; }
            }
            throw AiStopChecks.denied();
        } catch (RuntimeException exception) { throw AiStopChecks.denied(); }
    }

    /**
     * @param consumerBearer 宿主取得的独立消费凭据，不能拿原机器凭据代替
     * @param credential 专用v2票据 @param mode 服务端固定模式 @param messageId 该模式的原助手ID
     * 完成来源预检后才无锁消费；再按已消费原期限提交。失败不放回票据，不重试旧协议。
     */
    public void consume(String consumerBearer, String credential, AiChatMode mode, Long messageId) {
        try {
            outsideTransaction(); AiStopChecks.target(mode, messageId);
            // 独立消费者通过真实认证之后才允许读取票据元数据。
            AiStopMachineIdentity consumer = machines.authenticate(consumerBearer, receiver, AiStopMachineRole.CONSUMER);
            AiStopChecks.machine(consumer, receiver, AiStopMachineRole.CONSUMER);
            String digest = codec.digest(credential);
            String raw = tickets.read(digest);
            AiStopTicket ticket = codec.decode(raw);
            validateTicket(ticket, mode, messageId);
            recheckOriginal(ticket);
            AiCallerOrigin origin = origin(ticket);
            inScope(ticket, context -> precheck.verify(context, origin, mode, messageId,
                    ticket.getOriginalMachine().getAllowedAppIds()));
            // 预检事务结束后复核同一两原会话和消费会话；此处不持任何数据库锁。
            outsideTransaction();
            AiStopMachineIdentity currentConsumer = machines.recheck(consumer, receiver, AiStopMachineRole.CONSUMER);
            AiStopChecks.sameMachine(consumer, currentConsumer, receiver, AiStopMachineRole.CONSUMER);
            recheckOriginal(ticket);
            validateTicket(ticket, mode, messageId);
            if (!tickets.consume(digest, raw)) { throw AiStopChecks.denied(); }
            long expiry = Math.min(ticket.getExpiresAtMillis(),
                    Math.min(consumer.getExpiresAtMillis(), currentConsumer.getExpiresAtMillis()));
            AiStopChecks.future(expiry);
            inScope(ticket, context -> {
                AiStopChecks.future(expiry);
                commits.commit(new AiConsumedStopCommand(context, origin, ticket, expiry));
            });
        } catch (RuntimeException exception) { throw AiStopChecks.denied(); }
    }

    /** 不允许源重新签发会话、扩大应用或缩短原期限后继续使用旧票据。 */
    private void recheckOriginal(AiStopTicket ticket) {
        AiHostSession user = users.recheck(ticket.getUser(), receiver, ticket.getMode());
        AiStopChecks.sameUser(ticket.getUser(), user, receiver);
        AiStopMachineIdentity machine = machines.recheck(ticket.getOriginalMachine(), receiver, AiStopMachineRole.ORIGINAL);
        AiStopChecks.sameMachine(ticket.getOriginalMachine(), machine, receiver, AiStopMachineRole.ORIGINAL);
        if (ticket.getExpiresAtMillis() > Math.min(user.getExpiresAtMillis(), machine.getExpiresAtMillis())) {
            throw AiStopChecks.denied();
        }
        AiStopChecks.future(ticket.getExpiresAtMillis());
    }

    /** 固定v2、完整receiver、原身份/应用及用途/目标/期限必须同时匹配。 */
    private void validateTicket(AiStopTicket ticket, AiChatMode mode, Long messageId) {
        if (ticket == null || ticket.getVersion() != 2 || !receiver.equals(ticket.getReceiver())
                || ticket.getMode() != mode || !messageId.equals(ticket.getMessageId())
                || ticket.getIssuedAtMillis() <= 0 || ticket.getIssuedAtMillis() > System.currentTimeMillis()
                || ticket.getExpiresAtMillis() <= ticket.getIssuedAtMillis()
                || ticket.getExpiresAtMillis() - ticket.getIssuedAtMillis() > AiStopChecks.MAX_TTL) {
            throw AiStopChecks.denied();
        }
        AiStopChecks.user(ticket.getUser(), receiver);
        AiStopChecks.machine(ticket.getOriginalMachine(), receiver, AiStopMachineRole.ORIGINAL);
        if (ticket.getExpiresAtMillis() > Math.min(ticket.getUser().getExpiresAtMillis(),
                ticket.getOriginalMachine().getExpiresAtMillis())) { throw AiStopChecks.denied(); }
        AiStopChecks.future(ticket.getExpiresAtMillis());
    }

    /** 恢复原用户身份只用于本地阶段，重新捕获核对；缺少或未完成同步回调视为失败。 */
    private void inScope(AiStopTicket ticket, Consumer<AiInvocationContext> stage) {
        Thread owner = Thread.currentThread();
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean called = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        try {
            scopes.execute(ticket.getUser(), origin(ticket), context -> {
                if (Thread.currentThread() != owner || !active.get() || !called.compareAndSet(false, true)) {
                    throw AiStopChecks.denied();
                }
                AiStopChecks.context(context, ticket.getUser());
                AiStopChecks.future(ticket.getExpiresAtMillis());
                stage.accept(context);
                completed.set(true);
            });
        } finally {
            // 即使宿主错误保存回调，返回后或另一线程也不能迟到执行提交。
            active.set(false);
        }
        if (!completed.get()) { throw AiStopChecks.denied(); }
    }

    /** 通用模块只使用不透明身份，不调用旧Long getter或解析数字。 */
    private AiCallerOrigin origin(AiStopTicket ticket) {
        AiStopMachineIdentity machine = ticket.getOriginalMachine();
        return AiCallerOrigin.forIdentifiers(receiver.getTenantId(), ticket.getUser().getActorId(),
                machine.getClientRecordId(), machine.getClientId(), machine.getBusinessSystem(), machine.getEnvironment());
    }

    /** 远端认证、Redis读写/消费不得被调用方长事务包围。 */
    private void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw AiStopChecks.denied(); }
    }
}
