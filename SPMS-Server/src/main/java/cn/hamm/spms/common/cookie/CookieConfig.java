package cn.hamm.spms.common.cookie;

import cn.hamm.spms.core.DateTimeUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>Cookie 相关配置（自建，取代 airpower-cookie 的同名类）</h1>
 *
 * <p>
 * 2026-09-18 后端 airpower 退场第一批：从 {@code cn.hamm.airpower.cookie.CookieConfig}
 * 逐字搬过来，<b>默认值全部一致</b>（路径 {@code /}、身份 Cookie 名 {@code authorization-key}、
 * HttpOnly {@code true}、有效期一天、Secure {@code true}）。
 * </p>
 *
 * <p>
 * <b>配置前缀改为 {@code spms.cookie}</b>（原 {@code airpower.cookie}）：本仓的 yml 从未配置过
 * 那一组键（全仓 grep 为 0），所以改前缀不影响任何现网配置；同时明确「这是项目的 Cookie 配置，
 * 不是框架的」。若将来要覆盖，写在 {@code spms.cookie.*} 下。
 * </p>
 *
 * <p>
 * ⚠️ <b>Secure 默认 true</b>：本地 http 开发若不显式关闭，浏览器不会回传该 Cookie
 * （这是原实现的默认口径，未改）。本地按需在 {@code application-local.yml} 里覆盖。
 * </p>
 *
 * @author Hamm.cn
 * @see CookieHelper
 */
@Data
@Configuration
@ConfigurationProperties("spms.cookie")
public class CookieConfig {
    /**
     * Cookie 的路径
     */
    private String cookiePath = CookieHelper.DEFAULT_PATH;

    /**
     * 身份验证的 Cookie 名称
     */
    private String authCookieName = "authorization-key";

    /**
     * Cookie 的 HttpOnly 配置
     */
    private boolean cookieHttpOnly = true;

    /**
     * Cookie 有效期
     */
    private int cookieMaxAge = DateTimeUtil.SECOND_PER_DAY;

    /**
     * 使用 Https 方式的安全 Cookie
     */
    private boolean cookieSecurity = true;
}
