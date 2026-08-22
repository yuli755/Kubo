package cn.hamm.spms.config;

import cn.hamm.airpower.core.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * <h1>SpringDoc 响应拦截器</h1>
 * <p>
 * 优先于 CurdResponseInterceptor 处理 SpringDoc 端点，避免 ClassCastException
 * </p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class SpringDocResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        log.debug("SpringDocResponseAdvice.supports: {}", returnType.getMethod());
        return true; // 始终支持，但在 beforeBodyWrite 中过滤
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        String path = request.getURI().getPath();
        log.debug("SpringDocResponseAdvice.beforeBodyWrite: path={}", path);
        if (path != null && (path.startsWith("/v3/api-docs") || path.startsWith("/doc.html"))) {
            // SpringDoc 端点直接返回原始响应，不经过 CurdResponseInterceptor 处理
            log.info("SpringDocResponseAdvice: bypassing CurdResponseInterceptor for path: {}", path);
            return body;
        }
        return body;
    }
}