package io.github.yoyocw.aichatkit.module.ai.contract.model;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Replaceable model boundary; implementations own protocol and resources. */
public interface AiModelClient {
    boolean isConfigured(AiChatMode mode, String appId);
    AiModelResult stream(AiModelRequest request, Consumer<AiModelEvent> events, BooleanSupplier stillGenerating) throws IOException;
    void cancel(AiChatMode mode, Long messageId);
}
