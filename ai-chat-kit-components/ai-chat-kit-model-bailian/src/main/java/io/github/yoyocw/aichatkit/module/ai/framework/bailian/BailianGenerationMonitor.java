package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import okhttp3.Call;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * 本地调用的有界状态监测器。共享状态由宿主提供，不注册远程停止接口。
 * 探针必须维持可信身份隔离（显式 SQL 身份或宿主上下文）、读取已提交状态并限制查询超时。
 */
public final class BailianGenerationMonitor implements AutoCloseable {

    /** 两个守护线程检查消息状态；探针若修改宿主线程上下文，必须在 finally 中恢复。 */
    private final ScheduledThreadPoolExecutor executor;
    /** 限制已注册监测任务的数量。 */
    private final Semaphore capacity;
    /** 两次检查完成之间的最短间隔，单位毫秒。 */
    private final int intervalMillis;

    /**
     * @param intervalMillis 检查间隔，200 至 10000 毫秒
     * @param maxCalls 并发监测上限，1 至 1024
     * @throws IllegalArgumentException 参数越界，不在异常中回显配置原值
     */
    public BailianGenerationMonitor(int intervalMillis, int maxCalls) {
        if (intervalMillis < 200 || intervalMillis > 10000 || maxCalls < 1 || maxCalls > 1024) {
            throw new IllegalArgumentException("AI 生成状态监测配置无效");
        }
        this.intervalMillis = intervalMillis;
        this.capacity = new Semaphore(maxCalls);
        this.executor = new ScheduledThreadPoolExecutor(2, runnable -> {
            Thread thread = new Thread(runnable, "ai-generation-state");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    /**
     * 注册调用前先同步确认仍在生成；未知状态或检查失败时取消上游，不猜测继续授权。
     * @param call 当前实例即将执行的模型请求
     * @param stillGenerating 宿主可信探针，仅消息仍允许生成时返回 true
     * @return 调用结束时必须关闭的监测资源
     * @throws IOException 探针缺失、容量耗尽或调度器已经关闭
     */
    public BailianGenerationWatch watch(Call call, BooleanSupplier stillGenerating) throws IOException {
        if (stillGenerating == null || !capacity.tryAcquire()) {
            throw new IOException("AI 生成状态监测不可用");
        }
        try {
            check(call, stillGenerating);
            ScheduledFuture<?> task = executor.scheduleWithFixedDelay(
                    () -> check(call, stillGenerating), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
            return new BailianGenerationWatch(task, capacity);
        } catch (RuntimeException ex) {
            capacity.release();
            call.cancel();
            throw new IOException("AI 生成状态监测不可用");
        }
    }

    /** 探针异常不带入日志或模型异常链，避免泄露宿主查询及身份数据。 */
    private void check(Call call, BooleanSupplier stillGenerating) {
        if (call.isCanceled()) {
            return;
        }
        try {
            if (!stillGenerating.getAsBoolean()) {
                call.cancel();
            }
        } catch (RuntimeException ex) {
            call.cancel();
        }
    }

    /** 宿主销毁引擎时终止监测；当前数据库操作仍受宿主自己的超时控制。 */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
