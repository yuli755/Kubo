package cn.hamm.spms.framework.curd.interceptor.filter;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import cn.hamm.spms.framework.api.RequestUtil;
import cn.hamm.spms.core.enums.HttpMethod;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * <h1>缓存请求的过滤器</h1>
 *
 * @author Hamm.cn
 */
@WebFilter
@Slf4j
public class RequestFilter implements Filter {
    /**
     * 过滤器
     *
     * @param servletRequest  请求
     * @param servletResponse 响应
     * @param filterChain     过滤器链
     */
    @Override
    public final void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain
    ) {
        try {
            // 仅对POST、PUT等有body的请求做缓存，可根据需求调整
            HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
            String method = httpRequest.getMethod();
            if (HttpMethod.POST.name().equals(method) && !RequestUtil.isUploadRequest(servletRequest)) {
                // 包装请求，缓存请求体
                ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
                filterChain.doFilter(wrappedRequest, servletResponse);
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
