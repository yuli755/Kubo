package cn.hamm.spms.common.cookie;

import jakarta.servlet.http.Cookie;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <h1>Cookie 助手（自建，取代 airpower-cookie 的同名类）</h1>
 *
 * <p>
 * 2026-09-18 后端 airpower 退场第一批：从 {@code cn.hamm.airpower.cookie.CookieHelper}
 * 逐行搬过来，API 与行为完全一致（{@link #getCookie(String, String)} 按
 * {@link CookieConfig} 组装，{@link #getAuthorizeCookie(String)} 用配置里的身份 Cookie 名）。
 * </p>
 *
 * <p>
 * <b>为什么能整块搬走</b>：airpower 自己的 api/curd/redis 三个模块都不使用 CookieHelper /
 * CookieConfig（参考实现里 grep 为 0），用它的是本仓的 {@code UserController} / {@code UserService}。
 * 搬走时曾在 {@code Application} 里排除过 {@code cn.hamm.airpower.cookie.Auto}；
 * 2026-09-18 框架层自持后 airpower 已不在依赖里，该排除语句已删除 ——
 * 否则两边都会注册名为 {@code cookieHelper} 的 Bean，Spring 启动即冲突。
 * </p>
 *
 * @author Hamm.cn
 * @see CookieConfig
 */
@Component
public class CookieHelper {
    /**
     * 默认路径
     */
    public static final String DEFAULT_PATH = "/";

    @Autowired
    private CookieConfig cookieConfig;

    /**
     * 获取一个 Cookie
     *
     * @param key   Cookie 键
     * @param value Cookie 值
     * @return Cookie
     * @see CookieConfig
     */
    public final @NotNull Cookie getCookie(String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setHttpOnly(cookieConfig.isCookieHttpOnly());
        cookie.setMaxAge(cookieConfig.getCookieMaxAge());
        cookie.setSecure(cookieConfig.isCookieSecurity());
        cookie.setPath(cookieConfig.getCookiePath());
        return cookie;
    }

    /**
     * 获取一个身份验证的 Cookie
     *
     * @param value 身份串的值
     * @return Cookie
     * @see CookieConfig
     */
    public final @NotNull Cookie getAuthorizeCookie(String value) {
        return getCookie(cookieConfig.getAuthCookieName(), value);
    }
}
