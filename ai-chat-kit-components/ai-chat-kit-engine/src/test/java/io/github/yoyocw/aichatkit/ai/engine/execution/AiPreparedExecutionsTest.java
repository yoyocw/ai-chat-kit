package io.github.yoyocw.aichatkit.ai.engine.execution;
import io.github.yoyocw.aichatkit.ai.engine.transaction.*;
import io.github.yoyocw.aichatkit.module.ai.contract.execution.AiPreparedExecution;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class AiPreparedExecutionsTest {
    @Test void outerCommitControlsReadinessAndConsumptionIsOnce() {
        FixtureManager manager = new FixtureManager();
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        AtomicInteger calls = new AtomicInteger();
        AiPreparedExecution[] handle = new AiPreparedExecution[1];
        new TransactionTemplate(manager).execute(status -> {
            handle[0] = transactions.required(() -> AiPreparedExecutions.prepare(transactions, sink -> calls.incrementAndGet()));
            assertThrows(IllegalStateException.class, () -> handle[0].consume((event, data) -> {}));
            java.util.concurrent.FutureTask<Boolean> early = new java.util.concurrent.FutureTask<>(() -> {
                assertThrows(IllegalStateException.class, () -> handle[0].consume((event, data) -> {}));
                return true;
            });
            Thread consumer = new Thread(early);
            consumer.start();
            try { assertTrue(early.get(2, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (Exception failure) { throw new AssertionError("pre-commit consumption must reject without waiting", failure); }
            return null;
        });
        handle[0].consume((event, data) -> {});
        assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> handle[0].consume((event, data) -> {}));
    }
    @Test void rollbackInvalidatesAndAmbientConsumptionDoesNotSpendHandle() {
        FixtureManager manager = new FixtureManager();
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        AiPreparedExecution[] rolledBack = new AiPreparedExecution[1];
        new TransactionTemplate(manager).execute(status -> {
            rolledBack[0] = transactions.required(() -> AiPreparedExecutions.prepare(transactions, sink -> fail("rollback executed")));
            status.setRollbackOnly();
            return null;
        });
        assertThrows(IllegalStateException.class, () -> rolledBack[0].consume((event, data) -> {}));
        AiPreparedExecution ready = transactions.required(() -> AiPreparedExecutions.prepare(transactions, sink -> {}));
        new TransactionTemplate(manager).execute(status -> {
            assertThrows(IllegalStateException.class, () -> ready.consume((event, data) -> {}));
            return null;
        });
        ready.consume((event, data) -> {});
        assertThrows(IllegalStateException.class, () -> AiPreparedExecutions.prepare(transactions, sink -> {}));
    }
    @Test void executionFailureStillConsumesHandle() {
        FixtureManager manager = new FixtureManager();
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        AiPreparedExecution ready = transactions.required(() -> AiPreparedExecutions.prepare(transactions, sink -> { throw new IllegalArgumentException("failed"); }));
        assertThrows(IllegalArgumentException.class, () -> ready.consume((event, data) -> {}));
        assertThrows(IllegalStateException.class, () -> ready.consume((event, data) -> {}));
    }
    @Test void simultaneousConsumptionInvokesExactlyOnce() throws Exception {
        FixtureManager manager = new FixtureManager();
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AiPreparedExecution ready = transactions.required(() -> AiPreparedExecutions.prepare(transactions, sink -> calls.incrementAndGet()));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        Runnable consume = () -> {
            try { start.await(); ready.consume((event, data) -> {}); }
            catch (IllegalStateException expected) { rejected.incrementAndGet(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        };
        Thread first = new Thread(consume);
        Thread second = new Thread(consume);
        first.start(); second.start(); start.countDown();
        first.join(5000); second.join(5000);
        assertFalse(first.isAlive()); assertFalse(second.isAlive());
        assertEquals(1, calls.get()); assertEquals(1, rejected.get());
    }
    @Test void unrelatedActiveTransactionCannotPrepareExecution() {
        FixtureManager selected = new FixtureManager();
        FixtureManager unrelated = new FixtureManager();
        AiTransactionExecutor transactions = new AiTransactionExecutor(selected, AiTransactionMode.REUSE_HOST);
        new TransactionTemplate(unrelated).execute(status -> {
            assertThrows(IllegalStateException.class, () -> AiPreparedExecutions.prepare(transactions, sink -> {}));
            return null;
        });
    }
    @Test void committedHandleRemainsConsumableWhenEarlierAfterCommitFails() {
        FixtureManager manager = new FixtureManager();
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        AtomicInteger calls = new AtomicInteger();
        AiPreparedExecution[] handle = new AiPreparedExecution[1];
        IllegalArgumentException callbackFailure = new IllegalArgumentException("earlier callback failed");
        assertSame(callbackFailure, assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(manager).execute(status -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { throw callbackFailure; }
            });
            handle[0] = transactions.required(() -> AiPreparedExecutions.prepare(transactions, sink -> calls.incrementAndGet()));
            return null;
        })));
        assertEquals(1, manager.commits, "outer resource transaction really committed");
        handle[0].consume((event, data) -> {});
        assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> handle[0].consume((event, data) -> {}));
    }
    static class FixtureManager extends AbstractPlatformTransactionManager implements ResourceTransactionManager {
        private final Object factory = new Object();
        int commits;
        public Object getResourceFactory() { return factory; }
        protected Object doGetTransaction() { return new Holder(TransactionSynchronizationManager.hasResource(factory)); }
        protected boolean isExistingTransaction(Object transaction) { return ((Holder) transaction).active; }
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((Holder) transaction).active = true;
            TransactionSynchronizationManager.bindResource(factory, transaction);
        }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) {}
        protected void doCleanupAfterCompletion(Object transaction) { TransactionSynchronizationManager.unbindResource(factory); }
        static class Holder { boolean active; Holder(boolean active) { this.active = active; } }
    }
}
