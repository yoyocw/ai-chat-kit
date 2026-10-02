package io.github.yoyocw.aichatkit.module.ai.contract.model;

import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_CALL_FAILED;

/** Signal for the engine's existing one-time session recovery. */
public final class AiModelSessionExpiredException extends AiModelException {
    public AiModelSessionExpiredException() {
        super(BAILIAN_CALL_FAILED, true);
    }
}
