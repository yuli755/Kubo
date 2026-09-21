package cn.hamm.spms.common.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * <h1>自持 Redis 辅助工具</h1>
 *
 * <p>封装 Redis 常用操作（键值读写、过期、自增、发布等），底层直接委托给 Spring Data Redis 的 {@link RedisTemplate}。
 * 单机无 Redis 调试时 {@link #getRedisTemplate()} 返回 {@code null}，各方法自动安全降级。</p>
 *
 * @author Hamm.cn
 */
@Component
public class RedisHelper {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    public boolean hasKey(String key) {
        if (redisTemplate == null || key == null) {
            return false;
        }
        Boolean has = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(has);
    }

    public void set(String key, Object value) {
        if (redisTemplate == null || key == null) {
            return;
        }
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long expireSeconds) {
        if (redisTemplate == null || key == null) {
            return;
        }
        if (expireSeconds > 0) {
            redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
    }

    public Object get(String key) {
        if (redisTemplate == null || key == null) {
            return null;
        }
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        if (redisTemplate == null || key == null) {
            return;
        }
        redisTemplate.delete(key);
    }

    public void delete(Collection<String> keys) {
        if (redisTemplate == null || keys == null || keys.isEmpty()) {
            return;
        }
        redisTemplate.delete(keys);
    }

    public long increment(String key) {
        if (redisTemplate == null || key == null) {
            return 0L;
        }
        Long val = redisTemplate.opsForValue().increment(key);
        return val != null ? val : 0L;
    }

    public void publish(String channel, Object message) {
        if (redisTemplate == null || channel == null) {
            return;
        }
        redisTemplate.convertAndSend(channel, message);
    }
}
