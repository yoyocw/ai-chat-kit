package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单次调用的监测资源；随模型调用结束释放，重复关闭不多还并发额度。 */
public final class BailianGenerationWatch implements AutoCloseable {

    /** 本次定期检查任务。 */
    private final ScheduledFuture<?> task;
    /** 监测池的有界并发额度。 */
    private final Semaphore capacity;
    /** 资源是否已经释放。 */
    private final AtomicBoolean closed = new AtomicBoolean();

    /** @param task 定期任务 @param capacity 本次调用已经占用的额度 */
    BailianGenerationWatch(ScheduledFuture<?> task, Semaphore capacity) {
        this.task = task;
        this.capacity = capacity;
    }

    /** 取消后续检查；不中断正在执行的宿主数据库操作，其超时由宿主设置。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            task.cancel(false);
            capacity.release();
        }
    }
}
