package cn.hamm.spms;

import cn.hamm.spms.framework.curd.interceptor.filter.RequestFilter;
import cn.hamm.spms.common.Configs;
import cn.hamm.spms.common.filter.BearerTokenNormalizationFilter;
import cn.hamm.spms.common.filter.SecurityHeadersFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * <h1>全局配置</h1>
 *
 * @author Hamm.cn
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private RequestInterceptor requestInterceptor;

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(requestInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/v3/api-docs/**", "/doc.html", "/doc.html/**");
    }

    /**
     * 注册过滤器
     *
     * @return 过滤器对象
     */
    @Bean
    public FilterRegistrationBean<RequestFilter> getFilterRegistration() {
        FilterRegistrationBean<RequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestFilter());
        registration.addUrlPatterns("/*");
        // 通过拦截器排除路径
        return registration;
    }

    /**
     * Bearer 令牌规范化过滤器（审核项 A-2）：把 {@code Authorization: Bearer x} 归一为裸令牌 x。
     * 框架 {@code ApiController.getCurrentUserId()} 验的是请求头原文（final 方法，覆写不了），
     * 不归一则所有 my* 接口对标准 Bearer 客户端一律 401。必须排在所有读 Authorization 的
     * 过滤器/拦截器之前，故 order 取最小值段（SecurityHeadersFilter 是 0）。
     *
     * @return 过滤器注册对象
     */
    @Bean
    public FilterRegistrationBean<BearerTokenNormalizationFilter> getBearerTokenNormalizationFilterRegistration() {
        FilterRegistrationBean<BearerTokenNormalizationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BearerTokenNormalizationFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE + 100);
        return registration;
    }

    /**
     * 安全响应头过滤器（S4 第一批）：给所有响应注入 nosniff / Referrer-Policy 等零风险头。
     * CSP 不在本过滤器里（见 SecurityHeadersFilter 类注释），避免误伤前端内联脚本与图表。
     */
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> getSecurityHeadersFilterRegistration() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(0);
        return registration;
    }

}
