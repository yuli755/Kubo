package cn.hamm.spms.common.cookie;

import jakarta.servlet.http.Cookie;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.SpringVersion;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <h1>真实 Spring 配置绑定和 Servlet Cookie 的共享契约</h1>
 *
 * <p>只加载项目自己的两个生产组件；不启动完整应用、数据库或 Redis，也不使用旧 Cookie JAR 或 stub。</p>
 *
 * @author Hamm.cn
 */
public final class CookieContracts {
    private CookieContracts() {
    }

    @FunctionalInterface
    public interface Check {
        void run() throws Exception;
    }

    public record Case(String name, Check check) {
    }

    public static List<Case> cases() {
        return List.of(
                new Case("cookie/managed-runtime-versions", () -> {
                    equal("3.4.4", SpringBootVersion.getVersion());
                    equal("6.2.5", SpringVersion.getVersion());
                }),
                new Case("cookie/one-owned-bean-per-type", () -> {
                    try (var context = context(Map.of())) {
                        equal(1, context.getBeansOfType(CookieHelper.class).size());
                        equal(1, context.getBeansOfType(CookieConfig.class).size());
                        same(context.getBean(CookieHelper.class), context.getBean("cookieHelper"));
                        same(context.getBean(CookieConfig.class), context.getBean("cookieConfig"));
                    }
                }),
                new Case("cookie/authorization-defaults", () -> {
                    try (var context = context(Map.of())) {
                        Cookie cookie = context.getBean(CookieHelper.class).getAuthorizeCookie("opaque-token");
                        equal("authorization-key", cookie.getName());
                        equal("opaque-token", cookie.getValue());
                        equal("/", cookie.getPath());
                        equal(86400, cookie.getMaxAge());
                        equal(true, cookie.getSecure());
                        equal(true, cookie.isHttpOnly());
                    }
                }),
                new Case("cookie/plain-cookie-keeps-caller-name", () -> {
                    try (var context = context(Map.of())) {
                        Cookie cookie = context.getBean(CookieHelper.class).getCookie("trace", "value");
                        equal("trace", cookie.getName());
                        equal("value", cookie.getValue());
                        equal("/", cookie.getPath());
                    }
                }),
                new Case("cookie/current-prefix-binds-every-property", () -> {
                    try (var context = context(Map.of(
                            "spms.cookie.auth-cookie-name", "session-key",
                            "spms.cookie.cookie-path", "/api",
                            "spms.cookie.cookie-max-age", "300",
                            "spms.cookie.cookie-security", "false",
                            "spms.cookie.cookie-http-only", "false"))) {
                        Cookie cookie = context.getBean(CookieHelper.class).getAuthorizeCookie("opaque");
                        equal("session-key", cookie.getName());
                        equal("/api", cookie.getPath());
                        equal(300, cookie.getMaxAge());
                        equal(false, cookie.getSecure());
                        equal(false, cookie.isHttpOnly());
                    }
                }),
                new Case("cookie/relaxed-camel-case-binding", () -> {
                    try (var context = context(Map.of("spms.cookie.cookiePath", "/custom", "spms.cookie.cookieMaxAge", "90"))) {
                        Cookie cookie = context.getBean(CookieHelper.class).getAuthorizeCookie("x");
                        equal("/custom", cookie.getPath());
                        equal(90, cookie.getMaxAge());
                    }
                }),
                new Case("cookie/old-prefix-does-not-control-owned-component", () -> {
                    try (var context = context(Map.of("airpower.cookie.auth-cookie-name", "old", "airpower.cookie.cookie-max-age", "1"))) {
                        Cookie cookie = context.getBean(CookieHelper.class).getAuthorizeCookie("x");
                        equal("authorization-key", cookie.getName());
                        equal(86400, cookie.getMaxAge());
                    }
                }),
                new Case("cookie/logout-uses-same-name-path-and-empty-value", () -> {
                    try (var context = context(Map.of("spms.cookie.cookie-path", "/app"))) {
                        Cookie cookie = context.getBean(CookieHelper.class).getAuthorizeCookie("");
                        cookie.setMaxAge(0);
                        equal("authorization-key", cookie.getName());
                        equal("", cookie.getValue());
                        equal("/app", cookie.getPath());
                        equal(0, cookie.getMaxAge());
                        equal(true, cookie.isHttpOnly());
                    }
                }),
                new Case("cookie/same-site-remains-callers-responsibility", () -> {
                    try (var context = context(Map.of())) {
                        Cookie cookie = context.getBean(CookieHelper.class).getAuthorizeCookie("x");
                        equal(null, cookie.getAttribute("SameSite"));
                        equal(null, cookie.getDomain());
                        cookie.setAttribute("SameSite", "Lax");
                        equal("Lax", cookie.getAttribute("SameSite"));
                    }
                }),
                new Case("cookie/fresh-mutable-cookie-per-call", () -> {
                    try (var context = context(Map.of())) {
                        CookieHelper helper = context.getBean(CookieHelper.class);
                        Cookie first = helper.getAuthorizeCookie("a");
                        Cookie second = helper.getAuthorizeCookie("b");
                        check(first != second);
                        first.setMaxAge(0);
                        equal(86400, second.getMaxAge());
                        equal("b", second.getValue());
                    }
                }),
                new Case("cookie/actual-config-injection-not-hardcoded-copy", () -> {
                    try (var context = context(Map.of())) {
                        context.getBean(CookieConfig.class).setCookieMaxAge(42);
                        equal(42, context.getBean(CookieHelper.class).getAuthorizeCookie("x").getMaxAge());
                    }
                }),
                new Case("cookie/invalid-config-fails-context-refresh", () -> {
                    fails(RuntimeException.class, () -> {
                        try (var invalid = context(Map.of("spms.cookie.cookie-max-age", "not-a-number"))) {
                            throw new AssertionError("invalid configuration must not start: " + invalid.getId());
                        }
                    });
                }),
                new Case("cookie/context-configuration-is-not-global-static-state", () -> {
                    try (var first = context(Map.of("spms.cookie.auth-cookie-name", "first"));
                         var second = context(Map.of("spms.cookie.auth-cookie-name", "second"))) {
                        equal("first", first.getBean(CookieHelper.class).getAuthorizeCookie("x").getName());
                        equal("second", second.getBean(CookieHelper.class).getAuthorizeCookie("x").getName());
                    }
                }),
                new Case("cookie/servlet-validates-name-and-preserves-null-value", () -> {
                    try (var context = context(Map.of())) {
                        CookieHelper helper = context.getBean(CookieHelper.class);
                        fails(IllegalArgumentException.class, () -> helper.getCookie("invalid name", "x"));
                        equal(null, helper.getCookie("key", null).getValue());
                    }
                })
        );
    }

    /** 使用真实绑定后处理器与真实生产组件，不扫描全应用，不从外部环境继承测试属性。 */
    private static AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("cookie-contract", new LinkedHashMap<>(properties)));
        ConfigurationPropertiesBindingPostProcessor.register(context);
        context.register(CookieConfig.class, CookieHelper.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException | Error error) {
            context.close();
            throw error;
        }
    }

    public static void main(String[] args) throws Exception {
        int passed = 0;
        for (Case test : cases()) {
            test.check().run();
            System.out.println("PASS " + test.name());
            passed++;
        }
        if (passed == 0) {
            throw new AssertionError("No Cookie contracts executed");
        }
        System.out.println("Cookie contracts: " + passed + " passed (real Spring/Servlet slice; not full application startup)");
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual);
        }
    }

    private static void same(Object expected, Object actual) {
        check(expected == actual);
    }

    private static void check(boolean value) {
        if (!value) {
            throw new AssertionError("Cookie contract failed");
        }
    }

    private static void fails(Class<? extends Throwable> type, Check check) throws Exception {
        try {
            check.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return;
            }
            throw error;
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
