package cn.hamm.spms.common.redis;

import cn.hamm.spms.framework.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static cn.hamm.spms.common.exception.Errors.SERVICE_ERROR;

/**
 * <h1>自持 Redis 辅助工具</h1>
 *
 * <p>封装 Redis 常用操作（键值读写、过期、自增、发布等），底层直接委托给 Spring Data Redis 的 {@link RedisTemplate}。
 * 单机无 Redis 调试时 {@link #getRedisTemplate()} 返回 {@code null}，各方法自动安全降级。</p>
 * <p>提供轻量级分布式互斥锁（SET NX EX）与安全释放能力，保障仓储出入库与单据流转在高并发下的原子性与防重放。</p>
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

    /**
     * 分布式锁原子加锁（SET NX EX）
     *
     * @param key           锁键
     * @param value         锁持有者标识（如随机 UUID）
     * @param expireSeconds 超时时间（秒）
     * @return 是否获取锁成功
     */
    public boolean setIfAbsent(String key, Object value, long expireSeconds) {
        if (redisTemplate == null || key == null) {
            return true; // 无 Redis 时安全降级放行
        }
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 安全释放分布式锁（仅当锁持有者 token 匹配时才删除，防止误删他人锁）
     *
     * @param key   锁键
     * @param token 锁持有者标识
     * @return 是否释放成功
     */
    public boolean releaseLock(String key, Object token) {
        if (redisTemplate == null || key == null || token == null) {
            return true;
        }
        Object current = redisTemplate.opsForValue().get(key);
        if (Objects.equals(String.valueOf(token), String.valueOf(current))) {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        }
        return false;
    }

    /**
     * 在分布式锁保护下执行无返回值的任务
     *
     * @param lockKey        锁键
     * @param timeoutSeconds 锁超时时间（秒）
     * @param action         业务回调
     */
    public void runWithLock(String lockKey, long timeoutSeconds, Runnable action) {
        executeWithLock(lockKey, timeoutSeconds, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 在分布式锁保护下执行有返回值的任务
     *
     * @param lockKey        锁键
     * @param timeoutSeconds 锁超时时间（秒）
     * @param action         业务回调
     * @param <T>            返回值类型
     * @return 执行结果
     */
    public <T> T executeWithLock(String lockKey, long timeoutSeconds, Supplier<T> action) {
        if (redisTemplate == null) {
            return action.get();
        }
        String token = UUID.randomUUID().toString();
        boolean locked = setIfAbsent(lockKey, token, timeoutSeconds);
        if (!locked) {
            throw new BusinessException(SERVICE_ERROR, "资源正在处理中，请勿重复提交");
        }
        try {
            return action.get();
        } finally {
            releaseLock(lockKey, token);
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
