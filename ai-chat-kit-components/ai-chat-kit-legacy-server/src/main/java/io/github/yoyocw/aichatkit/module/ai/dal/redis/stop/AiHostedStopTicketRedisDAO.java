package io.github.yoyocw.aichatkit.module.ai.dal.redis.stop;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiHostedStopConstants.REDIS_KEY;

/** 独立停止票据存储；异常向上传播，不回退OAuth缓存，不执行应用层重试。 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.hosted-stop", name = "enabled", havingValue = "true")
public class AiHostedStopTicketRedisDAO {
    /** 复用现有Redis连接，无启动读写。 */
    private final StringRedisTemplate redisTemplate;
    /** 存在、原值相等且未过期才删除；单Redis键原子消费，不代表数据库事务原子。 */
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<Long>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] and redis.call('PTTL', KEYS[1]) > 0 then "
                    + "return redis.call('DEL', KEYS[1]) end return 0", Long.class);

    /**
     * NX创建票据，禁止覆盖；网络异常不当作碰撞重试。
     * @param digest 凭据SHA256小写十六进制
     * @param json 无凭据的元数据原始JSON
     * @param ttlMillis 正毫秒期限，不超过30000
     * @return true创建成功，false仅表示键已存在
     */
    public boolean create(String digest, String json, long ttlMillis) {
        if (json == null || ttlMillis <= 0 || ttlMillis > 30000) {
            throw new IllegalArgumentException("停止票据存储参数无效");
        }
        Boolean created = redisTemplate.opsForValue().setIfAbsent(key(digest), json, ttlMillis, TimeUnit.MILLISECONDS);
        if (created == null) { throw new IllegalStateException("停止票据创建结果未知"); }
        return created;
    }

    /** @param digest 凭据摘要 @return 未消费元数据原文，不存在或过期时为null */
    public String read(String digest) { return redisTemplate.opsForValue().get(key(digest)); }

    /**
     * 比较读取时的原始值，不重新序列化；同一票据最多一次成功。
     * @param digest 凭据摘要
     * @param originalJson 已经完成授权复核的原始元数据JSON
     * @return 仅原子删除一条时为true；异常或结果不明必须由上层拒绝
     */
    public boolean consume(String digest, String originalJson) {
        if (originalJson == null) { throw new IllegalArgumentException("停止票据消费参数无效"); }
        Long result = redisTemplate.execute(CONSUME, Collections.singletonList(key(digest)), originalJson);
        return Long.valueOf(1L).equals(result);
    }

    /** 键空间只能接收摘要，避免将原凭据作为Redis键。 */
    private String key(String digest) {
        if (digest == null || !digest.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("停止票据摘要无效");
        }
        return String.format(REDIS_KEY, digest);
    }
}
