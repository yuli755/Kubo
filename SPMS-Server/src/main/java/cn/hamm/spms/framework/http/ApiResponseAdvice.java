package cn.hamm.spms.framework.http;

import cn.hamm.spms.framework.util.Tracer;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * <h1>全局 HTTP 响应增强</h1>
 *
 * <p>自持响应体处理器，统一为 {@link ApiResponse} 注入当前请求的链路追踪标识（Trace ID）。</p>
 *
 * @author Hamm.cn
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ApiResponse.class.isAssignableFrom(returnType.getParameterType())
                && AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> result) {
            if (result.getTraceId() == null && Tracer.getTraceId() != null) {
                return result.withTraceId(Tracer.getTraceId());
            }
        }
        return body;
    }
}
