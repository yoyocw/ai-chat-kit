package io.github.yoyocw.aichatkit.module.ai.config;

/** Supplier-independent generation placeholder expiry policy. */
public final class AiExecutionPolicy {
    private final long staleGenerationSeconds;
    public AiExecutionPolicy(long staleGenerationSeconds) {
        if (staleGenerationSeconds <= 0) { throw new IllegalArgumentException("AI generation expiry must be positive"); }
        this.staleGenerationSeconds = staleGenerationSeconds;
    }
    public long getStaleGenerationSeconds() { return staleGenerationSeconds; }
}
