package cn.hamm.spms.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * <h1>请求体缓存过滤器</h1>
 *
 * <p>包装带有 Request Body 的请求（POST/PUT），便于多处重复读取。
 * 替代旧 {@code cn.hamm.airpower.curd.interceptor.filter.RequestFilter}。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
public class RequestFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        try {
            if (servletRequest instanceof HttpServletRequest httpRequest) {
                String method = httpRequest.getMethod();
                String contentType = httpRequest.getContentType();
                boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
                if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) && !isMultipart) {
                    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                    filterChain.doFilter(wrappedRequest, servletResponse);
                    return;
                }
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception e) {
            log.error("RequestFilter 异常", e);
            throw e;
        }
    }
}
