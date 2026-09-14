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
}
