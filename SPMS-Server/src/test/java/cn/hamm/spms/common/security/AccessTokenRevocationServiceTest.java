package cn.hamm.spms.common.security;

import cn.hamm.spms.framework.redis.RedisHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * <h1>AccessTokenRevocationService 契约测试</h1>
 *
 * <p>钉死吊销名单的四个不变量：吊销写入 {@code kubo:token:revoked:<sha256(令牌)>}
 * （存摘要不存明文凭证）；TTL 下限 1 秒；空令牌完全不碰 Redis（no-op）；
 * isRevoked 与 revoke 命中同一把 key（登出后再鉴权必须拦得住）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("AccessTokenRevocationService · 登出令牌吊销名单")
@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationServiceTest {

    /** SHA-256("abc")，JLS 保证所有实现一致，直接当硬编码断言用 */
    private static final String SHA256_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Mock
    private RedisHelper redisHelper;

    @InjectMocks
    private AccessTokenRevocationService service;

    @Test
    @DisplayName("吊销写入摘要 key：kubo:token:revoked:<sha256(令牌)>，值为占位符，TTL 透传")
    void revokeStoresDigestKeyWithTtl() {
        service.revoke("abc", 3600L);

        verify(redisHelper).set("kubo:token:revoked:" + SHA256_ABC, "1", 3600L);
    }

    @Test
    @DisplayName("TTL 非正数时钳到 1 秒：Redis 不接受非法 TTL")
    void revokeClampsNonPositiveTtl() {
        service.revoke("abc", 0L);

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(redisHelper).set(anyString(), anyString(), ttl.capture());
        assertEquals(1L, ttl.getValue());
    }

    @Test
    @DisplayName("空令牌吊销是 no-op：绝不碰 Redis")
    void revokeIgnoresBlankToken() {
        service.revoke(null, 10L);
        service.revoke("", 10L);
        service.revoke("   ", 10L);

        verifyNoInteractions(redisHelper);
    }

    @Test
    @DisplayName("登出后鉴权：revoke 写入的 key 必须被 isRevoked 原样命中")
    void isRevokedHitsTheSameKeyWrittenByRevoke() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        service.revoke("abc", 10L);
        verify(redisHelper).set(key.capture(), anyString(), anyLong());

        given(redisHelper.hasKey(key.getValue())).willReturn(true);
        assertTrue(service.isRevoked("abc"), "同一枚令牌吊销后必须判定为已吊销");

        // 另一枚令牌（不同摘要）不受影响
        assertFalse(service.isRevoked("abc-tampered"), "未吊销的令牌不得被误杀");
    }

    @Test
    @DisplayName("空令牌查询恒为未吊销，且不碰 Redis")
    void isRevokedIgnoresBlankToken() {
        assertFalse(service.isRevoked(null));
        assertFalse(service.isRevoked(""));
        assertFalse(service.isRevoked("   "));

        verifyNoInteractions(redisHelper);
    }
}
