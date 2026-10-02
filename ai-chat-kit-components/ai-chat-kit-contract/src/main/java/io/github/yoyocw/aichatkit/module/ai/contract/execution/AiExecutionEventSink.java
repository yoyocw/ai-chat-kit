package io.github.yoyocw.aichatkit.module.ai.contract.execution;
import java.util.Map;
/** Per-execution neutral output; implementations may propagate transport failures. */
@FunctionalInterface
public interface AiExecutionEventSink {
    void accept(String event, Map<String, Object> data);
}
