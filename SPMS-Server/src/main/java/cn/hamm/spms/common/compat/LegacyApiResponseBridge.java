package cn.hamm.spms.common.compat;

import cn.hamm.airpower.core.Json;
import cn.hamm.spms.framework.http.ApiResponse;
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
 * <h1>旧响应过滤链退场前的显式适配点</h1>
 *
 * <p>旧响应拦截器仅对 Json 执行 RootModel 白名单/脱敏/trace 处理。
 * 因此必须在它之前把新契约转换为旧对象，不能直接返回新 POJO 静默绕过字段保护。
 * 本类明确保留这一处旧依赖；接管过滤链并验收后再删除，不属于独立 framework。</p>
 *
 * @author Hamm.cn
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyApiResponseBridge implements ResponseBodyAdvice<Object> {
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
            return Json.show(result.getCode(), result.getMessage(), result.getData()).setTraceId(result.getTraceId());
        }
        return body;
    }
}
