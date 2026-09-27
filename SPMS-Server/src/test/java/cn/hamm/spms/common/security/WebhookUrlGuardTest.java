package cn.hamm.spms.common.security;

import cn.hamm.airpower.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <h1>WebhookUrlGuard 契约测试（K-2 SSRF 出站收敛）</h1>
 *
 * <p>全部用字面 IP，不依赖 DNS：内网/环回/链路本地（云 metadata）/组播/CGNAT/IPv6 ULA
 * 必须被拒；公网形态与 http(s) 协议放行；EMAIL 收件人禁多地址与 CRLF。</p>
 */
class WebhookUrlGuardTest {

    @Test
    @DisplayName("内网/环回/链路本地/全零 一律拒绝")
    void blocksPrivateAndLoopback() {
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://127.0.0.1/hook"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://10.0.0.5/hook"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://192.168.1.1/hook"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://172.16.0.1/hook"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://0.0.0.0/hook"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://[::1]/hook"));
    }

    @Test
    @DisplayName("云 metadata（169.254 链路本地）拒绝——SSRF 经典目标")
    void blocksCloudMetadata() {
        assertThrows(ServiceException.class,
                () -> WebhookUrlGuard.checkHttpUrl("http://169.254.169.254/latest/meta-data/iam/"));
    }

    @Test
    @DisplayName("CGNAT 100.64.0.0/10 与 IPv6 ULA fc00::/7 拒绝（Java 默认口径盖不住的两段）")
    void blocksCgnatAndUla() {
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://100.64.0.1/hook"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://100.127.255.255/hook"));
        // 100.128/9 不属于 CGNAT，但 100.64/10 边界内必须拒；边界外 100.63 不拒（公网段）
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://[fd00::1]/hook"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http://[fc12::34]/hook"));
    }

    @Test
    @DisplayName("非 http(s) 协议与畸形地址拒绝")
    void blocksNonHttpAndMalformed() {
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("ftp://93.184.216.34/file"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("file:///etc/passwd"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("gopher://93.184.216.34:11211/"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("不是URL"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkHttpUrl("http:///no-host"));
    }

    @Test
    @DisplayName("公网 http(s) 字面 IP 放行（不依赖 DNS）")
    void allowsPublicHttp() {
        assertDoesNotThrow(() -> WebhookUrlGuard.checkHttpUrl("https://93.184.216.34/hook"));
        assertDoesNotThrow(() -> WebhookUrlGuard.checkHttpUrl("http://93.184.216.34:8443/hook?x=1"));
    }

    @Test
    @DisplayName("EMAIL 收件人：单地址放行；多地址/空白/CRLF 注入拒绝")
    void emailRecipientContract() {
        assertDoesNotThrow(() -> WebhookUrlGuard.checkEmailRecipient("ops@example.com"));
        // CRLF 头注入
        assertThrows(ServiceException.class,
                () -> WebhookUrlGuard.checkEmailRecipient("ops@example.com\r\nBcc: victim@corp.com"));
        // 多地址（JavaMail 会逐个投递）
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkEmailRecipient("a@e.com, b@e.com"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkEmailRecipient("a b@e.com"));
        assertThrows(ServiceException.class, () -> WebhookUrlGuard.checkEmailRecipient(""));
    }
}
