package io.github.yoyocw.aichatkit.ai.engine.execution;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiExecutionEventSink;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Binds one execution to the actual selected AI transaction completion. */
public final class AiPreparedExecutions {
    private AiPreparedExecutions() {}
    public static AiPreparedExecution prepare(AiTransactionExecutor transactions, Consumer<AiExecutionEventSink> execution) {
        Objects.requireNonNull(transactions, "transactions").requireActive();
        Prepared prepared = new Prepared(Objects.requireNonNull(execution, "execution"));
        transactions.afterCommit(() -> prepared.state.compareAndSet(State.PENDING, State.READY));
        transactions.afterCompletion(status -> {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                // A preceding afterCommit callback may throw and short-circuit later callbacks.
                // Completion still proves the actual resource commit and must release the handle.
                prepared.state.compareAndSet(State.PENDING, State.READY);
            } else {
                prepared.state.compareAndSet(State.PENDING, State.INVALID);
            }
        });
        return prepared;
    }
    private enum State { PENDING, READY, INVALID, CONSUMED }
    private static final class Prepared implements AiPreparedExecution {
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
        private final Consumer<AiExecutionEventSink> execution;
        private Prepared(Consumer<AiExecutionEventSink> execution) { this.execution = execution; }
        public void consume(AiExecutionEventSink sink) {
            Objects.requireNonNull(sink, "sink");
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("AI execution must run outside an active transaction");
            }
            if (!state.compareAndSet(State.READY, State.CONSUMED)) {
                throw new IllegalStateException("AI prepared execution is not committed, was rolled back, or has already been consumed");
            }
            execution.accept(sink);
        }
    }
}
