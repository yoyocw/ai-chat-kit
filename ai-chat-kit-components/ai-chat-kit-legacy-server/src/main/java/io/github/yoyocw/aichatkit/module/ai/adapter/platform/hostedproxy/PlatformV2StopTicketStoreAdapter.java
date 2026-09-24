package io.github.yoyocw.aichatkit.module.ai.adapter.platform.hostedproxy;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopTicketStorePort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 真实Redis v2存储；与v1独立键空间，复用NX和比较原文/正TTL后删除的原子语义。 */
public final class PlatformV2StopTicketStoreAdapter implements AiStopTicketStorePort {
    /** 唯一真实宿主Redis连接，不提供内存替代。 */
    private final StringRedisTemplate redis;
    /** 编码部署命名空间的v2前缀，不包含原始票据。 */
    private final String prefix;
    /** 单Redis键比较并删除，不代表跨数据库事务原子。 */
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] and redis.call('PTTL', KEYS[1]) > 0 then "
                    + "return redis.call('DEL', KEYS[1]) end return 0", Long.class);

    /** @param redis 真实连接 @param namespace 固定部署命名空间，编码后隔离键 */
    public PlatformV2StopTicketStoreAdapter(StringRedisTemplate redis, String namespace) {
        this.redis = Objects.requireNonNull(redis);
        if (namespace == null || namespace.trim().isEmpty() || namespace.length() > 128) {
            throw PlatformV2StopSupport.failure();
        }
        this.prefix = "ai:hosted-stop:ticket:v2:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(namespace.getBytes(StandardCharsets.UTF_8)) + ":";
    }

    /** NX失败仅表示冲突，未知响应必须拒绝；不覆盖已有票据。 */
    @Override
    public boolean create(String digest, String rawMetadata, long ttlMillis) {
        metadata(rawMetadata);
        if (ttlMillis <= 0 || ttlMillis > 30000) { throw PlatformV2StopSupport.failure(); }
        Boolean created = redis.opsForValue().setIfAbsent(key(digest), rawMetadata, ttlMillis, TimeUnit.MILLISECONDS);
        if (created == null) { throw PlatformV2StopSupport.failure(); }
        return created;
    }

    /** 返回同一次读取的原文，不能重序列化后再消费。 */
    @Override
    public String read(String digest) { return redis.opsForValue().get(key(digest)); }

    /** 只接受删除一条，不重试未知结果、异常或已消费票据。 */
    @Override
    public boolean consume(String digest, String originalRawMetadata) {
        metadata(originalRawMetadata);
        Long result = redis.execute(CONSUME, Collections.singletonList(key(digest)), originalRawMetadata);
        return Long.valueOf(1L).equals(result);
    }

    /** 原文最大8192字符，只含身份元数据，不承载原始Token。 */
    private void metadata(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192) { throw PlatformV2StopSupport.failure(); }
    }

    /** 键只允许规范摘要，v1或原凭据不能进入此命名空间。 */
    private String key(String digest) {
        if (digest == null || !digest.matches("[a-f0-9]{64}")) { throw PlatformV2StopSupport.failure(); }
        return prefix + digest;
    }
}
