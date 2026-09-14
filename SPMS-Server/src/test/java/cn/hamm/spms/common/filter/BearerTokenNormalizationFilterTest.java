package cn.hamm.spms.common.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>Bearer 令牌规范化过滤器测试（审核项 A-2）</h1>
 *
 * <p>钉住契约：下游（框架 getCurrentUserId / RequestInterceptor / logout）读到的
 * Authorization 恒为裸令牌。这里直接用 MockFilterChain 捕获链上实际流转的请求对象，
 * 等价于框架在 controller 里 {@code request.getHeader("Authorization")} 拿到的值。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("BearerTokenNormalizationFilter · Authorization 头归一")
class BearerTokenNormalizationFilterTest {

    private final BearerTokenNormalizationFilter filter = new BearerTokenNormalizationFilter();

    private jakarta.servlet.http.HttpServletRequest doFilter(MockHttpServletRequest request) throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        // 链上流转的可能是包装后的 wrapper，只能按接口取
        return (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
    }

    @Test
    @DisplayName("标准 Bearer 前缀被剥离，下游拿到裸令牌")
    void bearerPrefixStripped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer eyJhbGciOi.abc.def");

        jakarta.servlet.http.HttpServletRequest downstream = doFilter(request);
        assertEquals("eyJhbGciOi.abc.def", downstream.getHeader("Authorization"));
    }

    @Test
    @DisplayName("scheme 大小写不敏感、多余空白容忍（RFC 7235）")
    void caseInsensitiveAndWhitespaceTolerant() throws Exception {
        for (String raw : new String[]{"bearer tok", "BEARER  tok ", "BeArEr tok\t"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", raw);
            assertEquals("tok", doFilter(request).getHeader("Authorization"),
                    "输入: " + raw.replace('\t', ' '));
        }
    }

    @Test
    @DisplayName("裸令牌原样透传（向后兼容，cookie-only/旧客户端不受影响）")
    void rawTokenUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "eyJhbGciOi.abc.def");

        assertEquals("eyJhbGciOi.abc.def", doFilter(request).getHeader("Authorization"));
    }

    @Test
    @DisplayName("Basic 等其它 scheme 不被动（OAuth2 客户端认证走 Basic）")
    void otherSchemesUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        assertEquals("Basic dXNlcjpwYXNz", doFilter(request).getHeader("Authorization"));
    }

    @Test
    @DisplayName("无 Authorization 头时不包装、不报错")
    void absentHeaderPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertNull(doFilter(request).getHeader("Authorization"));
    }

    @Test
    @DisplayName("getHeaders 枚举同样返回归一值（框架逐元素读取时也一致）")
    void enumerationNormalized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok-1");

        Enumeration<String> values = doFilter(request).getHeaders("Authorization");
        assertTrue(values.hasMoreElements());
        assertEquals("tok-1", values.nextElement());
        assertFalse(values.hasMoreElements(), "归一后必须只剩一个值，避免验签取到带前缀的旧值");
    }

    @Test
    @DisplayName("同名 query 参数同口径归一（框架支持令牌走参数，logout 取值顺序一致）")
    void parameterNormalized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok-1");
        request.setParameter("Authorization", "Bearer tok-2");

        jakarta.servlet.http.HttpServletRequest downstream = doFilter(request);
        assertEquals("tok-1", downstream.getHeader("Authorization"));
        assertEquals("tok-2", downstream.getParameter("Authorization"));
    }

    @Test
    @DisplayName("其它请求头不受影响")
    void otherHeadersUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer tok");
        request.addHeader("X-Tenant-Id", "7");

        assertEquals("7", doFilter(request).getHeader("X-Tenant-Id"));
    }
}
