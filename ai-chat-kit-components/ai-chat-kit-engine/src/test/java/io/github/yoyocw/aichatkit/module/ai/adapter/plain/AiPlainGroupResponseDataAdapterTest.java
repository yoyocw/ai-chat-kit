package io.github.yoyocw.aichatkit.module.ai.adapter.plain;

import io.github.yoyocw.aichatkit.module.ai.contract.model.AiModelException;
import org.junit.jupiter.api.Test;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.GROUP_WORKFLOW_OUTPUT_INVALID;
import static org.assertj.core.api.Assertions.*;

class AiPlainGroupResponseDataAdapterTest {
    @Test void invalidDisplayPayloadKeepsNeutralGroupFailureClassification() {
        AiPlainGroupResponseDataAdapter adapter = new AiPlainGroupResponseDataAdapter();
        for (String invalid : new String[]{"secret-untrusted-output", "[]", "{\"finalAnswer\":123}", "{\"completed\":\"secret\"}"}) {
            assertThatThrownBy(() -> adapter.build(invalid)).isInstanceOf(IllegalArgumentException.class)
                    .satisfies(failure -> {
                        // This is the same safe classifier used by the group executor's RuntimeException catch.
                        AiModelException safe = AiModelException.from(failure);
                        assertThat(safe.getErrorCode()).isEqualTo(GROUP_WORKFLOW_OUTPUT_INVALID);
                        assertThat(safe.isRetryable()).isFalse();
                        assertThat(safe.getCause()).isNull();
                        assertThat(failure.toString()).doesNotContain("secret-untrusted-output", "123", "secret");
                    });
        }
    }

    @Test void optionalDisplayAndControlledFieldsRemainAvailable() {
        AiPlainGroupResponseDataAdapter adapter = new AiPlainGroupResponseDataAdapter();
        assertThat(adapter.build(null)).isNull();
        assertThat(adapter.build("null")).isNull();
        assertThat(adapter.build("{\"completed\":true,\"finalAnswer\":\"done\",\"privateCredential\":\"secret\"}"))
                .contains("GROUP_CHAT_RESULT", "done", "true").doesNotContain("privateCredential", "secret");
    }
}
