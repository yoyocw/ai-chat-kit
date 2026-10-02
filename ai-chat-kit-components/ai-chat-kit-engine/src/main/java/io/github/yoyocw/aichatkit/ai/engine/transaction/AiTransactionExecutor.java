package io.github.yoyocw.aichatkit.ai.engine.transaction;

import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

/** AI专属短事务边界；不注册全局TM，不以线程上存在任意事务冒充AI事务。 */
public final class AiTransactionExecutor {
    /** 已由存储适配器验证的真实同源事务管理器。 */
    private final PlatformTransactionManager manager;
    /** 管理器使用的真实资源键，例如实际DataSource对象。 */
    private final Object resourceFactory;
    /** 同库复用或AI私有资源策略。 */
    private final AiTransactionMode mode;
    /** 宿主已证明属于选中数据源的附属资源键，例如同源SqlSessionFactory；按对象身份匹配。 */
    private final Set<Object> sameSourceResources;
    /** 仅此执行器使用的同步生命周期键。 */
    private final Object frameKey = new Object();

    /** @param manager 真实资源事务管理器 @param mode 明确复用策略；不支持无资源/noop管理器 */
    public AiTransactionExecutor(PlatformTransactionManager manager, AiTransactionMode mode) {
        this(manager, mode, Collections.emptySet());
    }
    /**
     * @param manager 真实管理器 @param mode 复用策略
     * @param sameSourceResourceFactories 宿主实际验证同源的附属资源键，不允许配置任意名称或通配符
     */
    public AiTransactionExecutor(PlatformTransactionManager manager, AiTransactionMode mode,
            Set<Object> sameSourceResourceFactories) {
        if (!(manager instanceof ResourceTransactionManager)) {
            throw new IllegalArgumentException("AI需要明确的资源事务管理器");
        }
        this.manager = manager;
        this.resourceFactory = Objects.requireNonNull(((ResourceTransactionManager) manager).getResourceFactory());
        this.mode = Objects.requireNonNull(mode);
        Set<Object> keys = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (Object key : Objects.requireNonNull(sameSourceResourceFactories)) {
            keys.add(Objects.requireNonNull(key));
        }
        this.sameSourceResources = Collections.unmodifiableSet(keys);
    }
    /** @return 所选真实管理器，仅供装配同源校验 */
    public PlatformTransactionManager manager() { return manager; }
    /** @return 实际资源键，不向HTTP输出 */
    public Object resourceFactory() { return resourceFactory; }
    /** @param work 本地原子用例 @return 用例结果；同源外层事务只参与、不提前提交 */
    public <T> T required(Supplier<T> work) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRED, false, TransactionDefinition.TIMEOUT_DEFAULT, work);
    }
    /** @param work 本地写入用例；异常传播并回滚 */
    public void runRequired(Runnable work) { required(() -> { work.run(); return null; }); }
    /** @param work 本地读取用例 @return 查询结果，保留REQUIRED同源参与语义 */
    public <T> T readOnly(Supplier<T> work) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRED, true, TransactionDefinition.TIMEOUT_DEFAULT, work);
    }
    /** @param work 独立尝试，例如分享码冲突重试 @return 独立AI短事务结果 */
    public <T> T requiresNew(Supplier<T> work) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, false, TransactionDefinition.TIMEOUT_DEFAULT, work);
    }
    /** @param work 包含分享访问计数的独立读取用例 @return READ_COMMITTED结果，允许真实计数写入 */
    public <T> T requiresNewReadCommitted(Supplier<T> work) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, false,
                TransactionDefinition.TIMEOUT_DEFAULT, TransactionDefinition.ISOLATION_READ_COMMITTED, work);
    }
    /** @param work 独立状态/来源读取 @param timeoutSeconds 秒，-1沿用管理器默认 @return 已提交快照 */
    public <T> T requiresNewReadOnly(Supplier<T> work, int timeoutSeconds) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, true, timeoutSeconds, work);
    }
    /** @param work 必须加入已有AI事务的存储步骤 @return 结果；绝不补建事务放行写入 */
    public <T> T mandatory(Supplier<T> work) {
        requireActive();
        return execute(TransactionDefinition.PROPAGATION_MANDATORY,
                TransactionSynchronizationManager.isCurrentTransactionReadOnly(), TransactionDefinition.TIMEOUT_DEFAULT, work);
    }
    /** 验证选中管理器真实的MANDATORY参与，不能仅依据全局active或hasResource。 */
    public void requireActive() {
        if (!ownsCurrent()) { throw new IllegalStateException("当前线程没有可参与的AI同源事务"); }
    }
    /** @param action 仅在选中AI事务真正提交后执行；外层回滚时不会执行 */
    public void afterCommit(Runnable action) {
        requireActive();
        installFrame();
        TransactionSynchronizationManager.registerSynchronization(new AiTransactionCommitAction(action));
    }
    /** Registers completion only after proving participation in the selected real AI transaction. */
    public void afterCompletion(java.util.function.IntConsumer action) {
        Objects.requireNonNull(action, "action");
        requireActive();
        installFrame();
        TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCompletion(int status) { action.accept(status); }
        });
    }
    /** 外部不同资源事务第一阶段明确拒绝，不隐式提交跨库部分结果。 */
    private <T> T execute(int propagation, boolean readOnly, int timeout, Supplier<T> work) {
        int isolation = propagation == TransactionDefinition.PROPAGATION_REQUIRES_NEW && readOnly
                ? TransactionDefinition.ISOLATION_READ_COMMITTED : TransactionDefinition.ISOLATION_DEFAULT;
        return execute(propagation, readOnly, timeout, isolation, work);
    }
    /** 保留公开分享的写计数及只读监控不同事务特征，不扩展为通用事务配置框架。 */
    private <T> T execute(int propagation, boolean readOnly, int timeout, int isolation, Supplier<T> work) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(propagation);
        definition.setReadOnly(readOnly);
        definition.setTimeout(timeout);
        definition.setIsolationLevel(isolation);
        return execute(definition, work);
    }
    /**
     * 保留旧用例的传播、隔离、超时及只读设置，同时执行相同的同源归属检查。
     * @param definition 服务端既有事务定义，不接受请求参数指定事务特征
     * @param work 本地事务工作；异常传播，不以未开启事务的方式继续
     * @return 事务工作结果；缺失定义或不能确认事务归属时拒绝
     */
    public <T> T execute(TransactionDefinition definition, Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        DefaultTransactionDefinition snapshot = new DefaultTransactionDefinition(
                Objects.requireNonNull(definition, "definition"));
        if (snapshot.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
            throw new IllegalArgumentException("AI事务超时无效");
        }
        boolean ambient = TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(resourceFactory);
        if (ambient && !ownsCurrent()) { throw new IllegalStateException("AI操作不能加入外部异库事务"); }
        TransactionTemplate template = new TransactionTemplate(manager, snapshot);
        return template.execute(status -> {
            installFrame();
            return work.get();
        });
    }
    /** 当前同步和所选资源必须同时真实有效；MANDATORY由实际TM判断自身事务。 */
    private boolean ownsCurrent() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) { return false; }
        Object resource = TransactionSynchronizationManager.getResource(resourceFactory);
        if (resource == null) { return false; }
        AiTransactionFrame frame = frame();
        if (frame != null ? !frame.matches(resource) : mode == AiTransactionMode.ISOLATED) { return false; }
        // 首次旧同库接入没有AI同步标记。别的TM可能仅挂起A的同步而留下A连接；
        // 因此A的MANDATORY成功也不能证明当前同步归属。未知资源存在即拒绝。
        if (frame == null && hasUnknownResource()) { return false; }
        DefaultTransactionDefinition mandatory = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_MANDATORY);
        mandatory.setReadOnly(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
        final TransactionStatus status;
        try { status = manager.getTransaction(mandatory); }
        catch (IllegalTransactionStateException absent) { return false; }
        // 已有事务的参与状态不会物理提交；管理器拒绝或rollbackOnly仍按真实语义传播。
        manager.commit(status);
        return true;
    }
    /** 允许同源MyBatis附属资源，但不按类名忽略潜在异库事务或任意线程资源。 */
    private boolean hasUnknownResource() {
        for (Object key : TransactionSynchronizationManager.getResourceMap().keySet()) {
            if (key != resourceFactory && !sameSourceResources.contains(key)) { return true; }
        }
        return false;
    }
    /** 在真实事务开始后记录当前资源；同步挂起/完成时由框架回调维护，不自行清空TSM。 */
    private void installFrame() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("AI事务管理器未建立真实同步");
        }
        Object resource = TransactionSynchronizationManager.getResource(resourceFactory);
        if (resource == null) { throw new IllegalStateException("AI事务资源未绑定"); }
        AiTransactionFrame previous = frame();
        if (previous != null && previous.matches(resource)) { return; }
        AiTransactionFrame current = new AiTransactionFrame(frameKey, previous, resource);
        if (previous != null) { TransactionSynchronizationManager.unbindResource(frameKey); }
        TransactionSynchronizationManager.bindResource(frameKey, current);
        TransactionSynchronizationManager.registerSynchronization(current);
    }
    /** @return 此执行器自己的生命周期标记，其他管理器不能伪造同源证明 */
    private AiTransactionFrame frame() {
        return (AiTransactionFrame) TransactionSynchronizationManager.getResource(frameKey);
    }
}
