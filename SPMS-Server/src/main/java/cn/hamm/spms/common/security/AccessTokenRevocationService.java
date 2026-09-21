package cn.hamm.spms.common.security;

import cn.hamm.spms.common.redis.RedisHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <h1>访问令牌吊销名单（登出即失效）</h1>
 *
 * <p>airpower 的访问令牌是自包含签名令牌：{@code /user/logout} 只删 cookie→userId 映射，
 * 已签发的令牌在自身有效期内继续有效 —— 被盗令牌"登出后照用"是审计里点的中危项。
 * 这里补上吊销侧：登出时把令牌摘要写入 Redis（TTL = 令牌最大有效期，之后名单条目
 * 与令牌同时死亡，零运维负担），鉴权时命中名单即 401。</p>
 *
 * <p>存摘要而非原文：令牌本身就是凭证，别在存储里再落一份可冒用的明文。
 * 单机调试未配 Redis 时 {@link RedisHelper#getRedisTemplate()} 返回 {@code null}，
 * 此时吊销是 no-op、鉴权视作未吊销 —— 与 {@code LoginBruteForceGuard} 同一降级口径
 * （本地开发优先，不能因为 Redis 缺席把所有人都锁在门外）。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class AccessTokenRevocationService {

    private static final String KEY_PREFIX = "kubo:token:revoked:";

    @Autowired
    private RedisHelper redisHelper;

    /**
     * 吊销一枚令牌。调用方负责传"裸令牌"（已剥离 Bearer 前缀）。
     *
     * @param rawToken    令牌原文
     * @param ttlSeconds  名单保留秒数（取令牌最大有效期即可）
     */
    public void revoke(String rawToken, long ttlSeconds) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        String key = buildKey(rawToken);
        redisHelper.set(key, "1", Math.max(ttlSeconds, 1));
        log.info("访问令牌已吊销: key={}", key);
    }

    /**
     * 令牌是否已被吊销。
     *
     * @param rawToken 裸令牌原文
     * @return true 表示登出名单里有它，必须拒绝
     */
    public boolean isRevoked(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return false;
        }
        return redisHelper.hasKey(buildKey(rawToken));
    }

    private String buildKey(String rawToken) {
        return KEY_PREFIX + sha256Hex(rawToken);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JLS 要求每个实现都支持的算法，走到这里说明 JVM 本身坏了
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
