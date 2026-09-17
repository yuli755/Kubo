package cn.hamm.spms.core.constant;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场【core 层自持】：整模块搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core。
 * 逻辑一字未改；本仓从此自己拥有这一层（上游变化不再自动进入）。改代码改这里。
 *
 * 为什么后来改成「整模块」而不是只搬调用闭包：框架层（curd/api/redis）也搬进来了，
 * 它要用的核心类必须都在 —— 半套副本只会把「缺哪个类」变成编译期的猜谜（见 docs 的可行性矩阵）。
 */

/**
 * <h1>HTTP 常量</h1>
 *
 * @author Hamm.cn
 */
public class HttpConstant {
    public static final String LOCAL_IP_ADDRESS = "127.0.0.1";

    public static final String LOCAL_HOST = "localhost";

    public static class Status {
        public static final int OK = 200;
        public static final int INTERNAL_SERVER_ERROR = 500;
    }

    /**
     * 授权类型
     */
    public static class GrantType {
        public static final String PASSWORD = "password";
        public static final String REFRESH_TOKEN = "refresh_token";
        public static final String CLIENT_CREDENTIALS = "client_credentials";
        public static final String AUTHORIZATION_CODE = "authorization_code";
        public static final String IMPLICIT = "implicit";
        public static final String BEARER = "Bearer";
        public static final String BASIC = "Basic";
    }

    /**
     * 请求头
     */
    public static class Header {
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String COOKIE = "Cookie";
        public static final String REQUEST_ID = "X-Request-ID";
        public static final String TRACE_ID = "X-Trace-ID";
        public static final String AUTHORIZATION = "Authorization";
        public static final String USER_AGENT = "User-Agent";
    }

    /**
     * 内容类型
     */
    public static class ContentType {
        public static final String APPLICATION_JSON = "application/json";
        public static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";
        public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
        public static final String MULTIPART_FORM_DATA = "multipart/form-data";
        public static final String TEXT_HTML = "text/html";
        public static final String TEXT_PLAIN = "text/plain";
    }

    /**
     * 代理
     */
    public static class Proxy {
        /**
         * 代理头
         */
        public static class Header {
            public static final String X_FORWARDED_FOR = "X-Forwarded-For";
            public static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
            public static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
            public static final String HTTP_CLIENT_IP = "HTTP_CLIENT_IP";
            public static final String HTTP_X_FORWARDED_FOR = "HTTP_X_FORWARDED_FOR";
        }
    }
}
