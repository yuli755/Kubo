package cn.hamm.spms.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 阶段 F-1：客户端 IP 解析器契约测试。
 *
 * <p>核心不变量：转发头（X-Forwarded-For / X-Real-IP）只有当<b>直连对端</b>命中
 * 可信代理白名单时才被采信；白名单外伪造的转发头必须原样返回直连地址。</p>
 */
@DisplayName("ClientIpResolver · 可信代理门控")
class ClientIpResolverTest {

    private static HttpServletRequest request(String remoteAddr, String xff, String realIp) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getRemoteAddr()).willReturn(remoteAddr);
        given(request.getHeader("X-Forwarded-For")).willReturn(xff);
        given(request.getHeader("X-Real-IP")).willReturn(realIp);
        return request;
    }

    @Test
    @DisplayName("默认空白名单：任何转发头都不采信")
    void emptyWhitelistNeverTrustsForwardedHeaders() {
        ClientIpResolver resolver = new ClientIpResolver("");
        HttpServletRequest req = request("203.0.113.9", "1.2.3.4", "8.8.8.8");
        assertEquals("203.0.113.9", resolver.resolve(req));
    }

    @Test
    @DisplayName("对端精确命中白名单：采信 XFF 首个合法条目，unknown 条目跳过")
    void exactMatchTrustedProxyTrustsXff() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1, 10.0.0.5");
        HttpServletRequest req = request("127.0.0.1", "unknown, 9.9.9.9, 5.6.7.8", null);
        assertEquals("9.9.9.9", resolver.resolve(req));
    }

    @Test
    @DisplayName("CIDR 网段命中与未命中")
    void cidrMatching() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8, 192.168.1.0/24");
        assertTrue(resolver.isTrustedProxy("10.20.30.40"));
        assertTrue(resolver.isTrustedProxy("192.168.1.7"));
        assertFalse(resolver.isTrustedProxy("11.0.0.1"));
        assertFalse(resolver.isTrustedProxy("192.168.2.7"));
        // IPv6 对端不参与网段匹配、也不抛异常
        assertFalse(resolver.isTrustedProxy("0:0:0:0:0:0:0:1"));

        HttpServletRequest viaSubnet = request("10.1.2.3", "1.2.3.4", null);
        assertEquals("1.2.3.4", resolver.resolve(viaSubnet));
        HttpServletRequest direct = request("11.0.0.1", "1.2.3.4", null);
        assertEquals("11.0.0.1", resolver.resolve(direct));
    }

    @Test
    @DisplayName("/32 单址段包含自身")
    void singleAddressCidrContainsItself() {
        ClientIpResolver resolver = new ClientIpResolver("172.16.0.9/32");
        assertTrue(resolver.isTrustedProxy("172.16.0.9"));
    }

    @Test
    @DisplayName("非法配置项忽略不抛异常")
    void invalidCidrIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("not-a-cidr/8, 127.0.0.1");
        assertFalse(resolver.isTrustedProxy("1.2.3.4"));
        assertTrue(resolver.isTrustedProxy("127.0.0.1"));
    }

    @Test
    @DisplayName("可信对端无 XFF 时回落 X-Real-IP，再回落直连地址")
    void trustedProxyFallbackChain() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");
        assertEquals("8.8.8.8", resolver.resolve(request("127.0.0.1", null, "8.8.8.8")));
        assertEquals("127.0.0.1", resolver.resolve(request("127.0.0.1", "  ", null)));
    }
}
