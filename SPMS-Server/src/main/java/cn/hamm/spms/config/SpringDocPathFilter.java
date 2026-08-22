package cn.hamm.spms.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * <h1>SpringDoc 路径过滤器</h1>
 * <p>
 * 优先于 Curd 过滤器处理 SpringDoc 端点，避免 Curd 拦截器/过滤器干扰
 * </p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class SpringDocPathFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // SpringDoc 路径直接放行，不经过 Curd 过滤器/拦截器
        if (path != null && (path.startsWith("/v3/api-docs") || path.startsWith("/doc.html"))) {
            log.info("SpringDocPathFilter: bypassing Curd filter for path: {}", path);
            chain.doFilter(request, response);
            return;
        }

        chain.doFilter(request, response);
    }
}