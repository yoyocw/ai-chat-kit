package io.github.yoyocw.aichatkit.module.ai.enums;

/** AI 自有停止协议隔离标识，不读写 system 原停止票据。 */
public final class AiHostedStopConstants {
    /** SHA256 摘要键命名空间；占位符只能是64位小写十六进制摘要。 */
    public static final String REDIS_KEY = "ai:hosted-stop:ticket:v1:%s";
    private AiHostedStopConstants() { }
}
