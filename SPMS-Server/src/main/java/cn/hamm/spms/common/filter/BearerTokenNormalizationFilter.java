package cn.hamm.spms.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * <h1>Bearer 令牌规范化过滤器（审核项 A-2）</h1>
 *
 * <p>框架 <code>ApiController.getCurrentUserId()</code>（protected final，无法覆写）直接拿
 * {@code Authorization} 请求头原文交给 <code>AccessTokenUtil.verify()</code>，而 verify 的第一步是
 * Base64 解码——"Bearer eyJ..." 必然解码失败，于是所有 {@code @Permission(authorize = false)} 且依赖
 * {@code getCurrentUserId()} 的接口（getMyInfo / getMyMenuList / getMyPermissionList /
 * getMyTenantList / updateMyInfo / updateMyPassword / getMyThirdList）对标准 Bearer 客户端一律 401
 * 「身份令牌无效」，纯 API 与前端 SPA 全部中招；裸令牌反而能用——与 OpenAPI 契约（Bearer securityScheme）
 * 直接矛盾。</p>
 *
 * <p>修复口径：在容器边缘把 {@code Authorization: Bearer x}（大小写不敏感、容忍多余空白）一次性归一为裸令牌
 * {@code x}，下游框架与业务代码从此只见一种形态。{@code RequestInterceptor.getVerifiedToken} 与
 * logout 里的剥离逻辑保留为纵深防御（对已归一的值是幂等操作）。</p>
 *
 * <p>刻意不动其它 scheme：{@code Basic}（OAuth2 客户端认证）、{@code Digest} 等原样透传；
 * 无 Bearer 前缀的值（裸令牌、cookie-only 场景的空头）也原样透传，行为严格向后兼容。</p>
 *
 * @author Hamm.cn
 */
public class BearerTokenNormalizationFilter implements Filter {

    private static final String BEARER_PREFIX = "bearer ";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String raw = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
            String normalized = stripBearerPrefix(raw);
            if (normalized != null && !normalized.equals(raw)) {
                request = new NormalizedAuthorizationRequest(httpRequest, normalized);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 剥离 Bearer 前缀（RFC 7235 scheme 大小写不敏感；容忍 "Bearer  x" 之类多余空白）
     *
     * @param value 请求头/参数原值
     * @return 归一后的裸令牌；非 Bearer 形态原样返回
     */
    static String stripBearerPrefix(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= BEARER_PREFIX.length()
                && trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return trimmed.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    /**
     * 只改写 Authorization 的读取视图，其余全部委托原请求
     */
    private static final class NormalizedAuthorizationRequest extends HttpServletRequestWrapper {
        private final String normalizedAuthorization;

        private NormalizedAuthorizationRequest(HttpServletRequest request, String normalizedAuthorization) {
            super(request);
            this.normalizedAuthorization = normalizedAuthorization;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return normalizedAuthorization;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(normalizedAuthorization));
            }
            return super.getHeaders(name);
        }

        @Override
        public String getParameter(String name) {
            // 框架允许令牌走同名 query 参数（见 UserController.logout 的取值顺序），同口径归一
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return stripBearerPrefix(super.getParameter(name));
            }
            return super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                String[] values = super.getParameterValues(name);
                if (values == null) {
                    return null;
                }
                String[] normalized = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    normalized[i] = stripBearerPrefix(values[i]);
                }
                return normalized;
            }
            return super.getParameterValues(name);
        }
    }
}
