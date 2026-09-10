package cn.hamm.spms.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <h1>DatabaseCredentialGuard 契约测试</h1>
 *
 * <p>锁住数据库凭据卫生底线：生产环境不得以空密码 / 源码弱默认密码 {@code kubo} 启动。</p>
 *
 * <p>刻意不测「密码强度 / 长度」：本守卫只拦「源码里明写的那个默认值」，不越权替运维定口令策略。</p>
 */
class DatabaseCredentialGuardTest {

    @Test
    @DisplayName("空密码被拒绝")
    void shouldRejectBlank() {
        assertThrows(IllegalStateException.class, () -> DatabaseCredentialGuard.validate(null));
        assertThrows(IllegalStateException.class, () -> DatabaseCredentialGuard.validate(""));
        assertThrows(IllegalStateException.class, () -> DatabaseCredentialGuard.validate("   "));
    }

    @Test
    @DisplayName("源码弱默认密码 kubo 被拒绝")
    void shouldRejectWeakDefault() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseCredentialGuard.validate("kubo"));
    }

    @Test
    @DisplayName("真实强密码被放行（不做强度/长度校验）")
    void shouldAcceptRealPassword() {
        assertDoesNotThrow(() -> DatabaseCredentialGuard.validate("P@ssw0rd!prod-2026"));
        assertDoesNotThrow(() -> DatabaseCredentialGuard.validate("a-short-but-not-default"));
    }
}
