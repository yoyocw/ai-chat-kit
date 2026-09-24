package io.github.yoyocw.aichatkit.compat.framework.mq.redis.config;

import io.github.yoyocw.aichatkit.compat.framework.mq.redis.core.RedisMQTemplate;
import io.github.yoyocw.aichatkit.compat.framework.mq.redis.core.interceptor.RedisMessageInterceptor;
import io.github.yoyocw.aichatkit.compat.framework.redis.config.AiChatKitRedisAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Redis 消息队列 Producer 配置类
 *
 * @author kelecc
 */
@Slf4j
@AutoConfiguration(after = AiChatKitRedisAutoConfiguration.class)
public class AiChatKitRedisMQProducerAutoConfiguration {

    @Bean
    public RedisMQTemplate redisMQTemplate(StringRedisTemplate redisTemplate,
                                           List<RedisMessageInterceptor> interceptors) {
        RedisMQTemplate redisMQTemplate = new RedisMQTemplate(redisTemplate);
        // 添加拦截器
        interceptors.forEach(redisMQTemplate::addInterceptor);
        return redisMQTemplate;
    }

}
