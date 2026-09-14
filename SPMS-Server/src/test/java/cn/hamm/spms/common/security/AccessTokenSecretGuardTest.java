package cn.hamm.spms.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <h1>AccessTokenSecretGuard 契约测试</h1>
 *
 * <p>锁住密钥卫生底线：生产环境不得以弱默认密钥 / 过短密钥启动。</p>
 */
class AccessTokenSecretGuardTest {

    @Test
    @DisplayName("空密钥被拒绝")
    void shouldRejectBlank() {
        assertThrows(IllegalStateException.class, () -> AccessTokenSecretGuard.validate(null));
        assertThrows(IllegalStateException.class, () -> AccessTokenSecretGuard.validate(""));
        assertThrows(IllegalStateException.class, () -> AccessTokenSecretGuard.validate("   "));
    }

    @Test
    @DisplayName("默认弱密钥 1234567890 被拒绝")
    void shouldRejectWeakDefault() {
        assertThrows(IllegalStateException.class,
                () -> AccessTokenSecretGuard.validate("1234567890"));
    }

    @Test
    @DisplayName("过短密钥被拒绝（< 32 字符）")
    void shouldRejectShort() {
        assertThrows(IllegalStateException.class,
                () -> AccessTokenSecretGuard.validate("a".repeat(31)));
    }

    @Test
    @DisplayName("足够长的强随机密钥被放行")
    void shouldAcceptStrong() {
        assertDoesNotThrow(() -> AccessTokenSecretGuard.validate("a".repeat(32)));
        assertDoesNotThrow(() -> AccessTokenSecretGuard.validate("0f1e2d3c4b5a69788796a5b4c3d2e1f0"));
    }

    /**
     * C-1 回归：守卫不再挂 @Profile("production")。忘设 SPRING_PROFILES_ACTIVE 的
     * 部署（即默认 profile + 弱默认密钥）必须在启动阶段 fail-fast，
     * 只有显式 allow-weak-default-secret=true（本地开发）才放行。
     */
    private static AccessTokenSecretGuard guard(String secret, boolean allowWeak) throws Exception {
        AccessTokenSecretGuard guard = new AccessTokenSecretGuard();
        java.lang.reflect.Field secretField = AccessTokenSecretGuard.class.getDeclaredField("accessTokenSecret");
        secretField.setAccessible(true);
        secretField.set(guard, secret);
        java.lang.reflect.Field allowField = AccessTokenSecretGuard.class.getDeclaredField("allowWeakDefaultSecret");
        allowField.setAccessible(true);
        allowField.setBoolean(guard, allowWeak);
        return guard;
    }

    @Test
    @DisplayName("未放行 + 默认弱密钥：run 拒绝启动（profile 缺省也拦得住）")
    void shouldFailFastWithoutOptOut() throws Exception {
        AccessTokenSecretGuard guard = guard("1234567890", false);
        assertThrows(IllegalStateException.class, () -> guard.run(null));
    }

    @Test
    @DisplayName("本地显式放行：run 放行弱默认密钥")
    void shouldAllowWithOptOut() throws Exception {
        AccessTokenSecretGuard guard = guard("1234567890", true);
        assertDoesNotThrow(() -> guard.run(null));
    }

    @Test
    @DisplayName("未放行 + 强密钥：run 正常通过")
    void shouldPassWithStrongSecret() throws Exception {
        AccessTokenSecretGuard guard = guard("0f1e2d3c4b5a69788796a5b4c3d2e1f0", false);
        assertDoesNotThrow(() -> guard.run(null));
    }
}
