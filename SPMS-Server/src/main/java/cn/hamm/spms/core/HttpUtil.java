package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

import cn.hamm.spms.core.constant.HttpConstant;
import cn.hamm.spms.core.constant.HttpConstant.Header;
import cn.hamm.spms.core.enums.HttpMethod;
import cn.hamm.spms.core.exception.ServiceException;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import static cn.hamm.spms.core.enums.HttpMethod.GET;

/**
 * <h1>HTTP 请求工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Data
@Accessors(chain = true, makeFinal = true)
public class HttpUtil {
    private static volatile HttpClient httpClient;

    /**
     * 请求头
     */
    private Map<String, Object> headers = new HashMap<>();

    /**
     * Cookie
     */
    private Map<String, Object> cookies = new HashMap<>();

    /**
     * 请求地址
     */
    private String url;

    /**
     * 请求体
     */
    private String body = "";

    /**
     * 请求方法
     */
    private HttpMethod method = GET;

    /**
     * 请求体类型
     */
    private String contentType = HttpConstant.ContentType.APPLICATION_JSON_UTF8;

    /**
     * 禁止外部实例化
     */
    private HttpUtil() {
    }

    /**
     * 创建一个 HttpUtil 对象
     *
     * @return HttpUtil
     */
    @Contract(" -> new")
    public static @NotNull HttpUtil create() {
        return new HttpUtil();
    }

    /**
     * 添加  Cookie
     *
     * @param key   Cookie 键
     * @param value Cookie 值
     * @return HttpUtil
     */
    @Contract("_, _ -> this")
    public final HttpUtil addCookie(String key, String value) {
        cookies.put(key, value);
        return this;
    }

    /**
     * 发送 POST 请求
     *
     * @return HttpResponse
     */
    public final @NotNull HttpResponse<String> post() {
        method = HttpMethod.POST;
        return send();
    }

    /**
     * 发送 POST 请求
     *
     * @param body 请求体
     * @return HttpResponse
     */
    @SuppressWarnings("UnusedReturnValue")
    public final @NotNull HttpResponse<String> post(String body) {
        method = HttpMethod.POST;
        this.body = body;
        return send();
    }

    /**
     * 发送 GET 请求
     *
     * @return HttpResponse
     */
    public final @NotNull HttpResponse<String> get() {
        method = GET;
        return send();
    }

    /**
     * 发送请求
     *
     * @return HttpResponse
     */
    public final @NotNull HttpResponse<String> send() {
        try {
            HttpResponse<String> response = getHttpClient().send(getHttpRequest(), HttpResponse.BodyHandlers.ofString());
            log.info("响应 {} {}", response.statusCode(), response.body());
            return response;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException("发起请求失败，" + e.getMessage());
        }
    }

    /**
     * 获取 HttpRequest 对象
     *
     * @return HttpRequest
     */
    private HttpRequest getHttpRequest() {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach((key, value) -> requestBuilder.header(key, value.toString()));
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
        log.info("请求地址: {} {}", method.name(), url);
        log.info("请求头: {}", headers);
        log.info("请求体: {}", body);
        switch (method) {
            case GET -> requestBuilder.GET();
            case POST -> requestBuilder.POST(bodyPublisher);
            case PUT -> requestBuilder.PUT(bodyPublisher);
            case DELETE -> requestBuilder.DELETE();
            default -> throw new ServiceException("不支持的请求方法");
        }
        if (Objects.nonNull(cookies)) {
            List<String> cookieList = new ArrayList<>();
            cookies.forEach((key, value) -> cookieList.add(key + "=" + value));
            requestBuilder.setHeader(
                    Header.COOKIE, String.join("; ", cookieList)
            );
        }
        if (Objects.nonNull(contentType)) {
            requestBuilder.header(Header.CONTENT_TYPE, contentType);
        }
        return requestBuilder.build();
    }

    /**
     * 获取 HttpClient
     *
     * @return HttpClient
     */
    private HttpClient getHttpClient() {
        if (Objects.isNull(httpClient)) {
            synchronized (HttpUtil.class) {
                if (Objects.isNull(httpClient)) {
                    HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
                    httpClientBuilder.connectTimeout(Duration.ofSeconds(5));
                    httpClient = httpClientBuilder.build();
                }
            }
        }
        return httpClient;
    }

    /**
     * 添加 Header
     *
     * @param key   Header 键
     * @param value Header 值
     * @return HttpUtil
     */
    @Contract("_, _ -> this")
    public final HttpUtil addHeader(String key, Object value) {
        headers.put(key, value);
        return this;
    }
}
