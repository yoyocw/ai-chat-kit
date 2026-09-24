package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

/** 专用v2一次性票据存储，必须真实持久化且单键原子消费，不允许内存后备或v1兼容。 */
public interface AiStopTicketStorePort {
    /**
     * @param digest 凭据SHA256小写十六进制，不是凭据原文
     * @param rawMetadata 最大8192字符的无Token元数据JSON @param ttlMillis 1..30000毫秒
     * @return 仅NX冲突为false；网络异常或未知结果必须抛出，禁止当碰撞重试
     */
    boolean create(String digest, String rawMetadata, long ttlMillis);

    /** @param digest v2凭据摘要 @return 原始元数据，不存在或已过期为null，故障必须抛出 */
    String read(String digest);

    /**
     * @param digest v2凭据摘要 @param originalRawMetadata 读取并验证时的原始值，不能重新序列化
     * @return 原值完全一致且TTL>0时原子删除一条才为true；其它结果拒绝，不得放回票据
     */
    boolean consume(String digest, String originalRawMetadata);
}
