package io.github.yoyocw.aichatkit.module.ai.contract.execution;
/** A prepared execution can be consumed once, only after its preparation transaction commits. */
@FunctionalInterface
public interface AiPreparedExecution {
    void consume(AiExecutionEventSink sink);
}
