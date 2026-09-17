package cn.hamm.spms;

import cn.hamm.spms.common.Configs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * <h1>入口类</h1>
 *
 * @author Hamm.cn
 */
@SpringBootApplication(
        // 2026-09-18 后端 airpower 退场第一批：Cookie 能力已由 cn.hamm.spms.common.cookie 自建接管
        // （CookieHelper / CookieConfig 与原实现逐行一致，仅配置前缀改为 spms.cookie）。
        // 这里必须排除 airpower 的 cookie 自动装配，否则它会 ComponentScan 出同名的
        // cookieHelper / cookieConfig Bean —— Spring 默认禁止 Bean 覆盖，启动即冲突。
        exclude = cn.hamm.airpower.cookie.Auto.class
)
@EnableScheduling
public class Application {
    /**
     * 服务器上下文对象
     */
    private static ServletWebServerApplicationContext serverApplicationContext;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        if (serverApplicationContext != null) {
            int port = serverApplicationContext.getWebServer().getPort();
            System.out.println("------------------------------------------");
            System.out.println("   Hi Guy, " + Configs.getAppConfig().getProjectName() + " is running at [" + port + "] !");
            System.out.println("------------------------------------------");
        }
    }

    @Autowired(required = false)
    public void autorun(
            ServletWebServerApplicationContext serverWebServerApplicationContext
    ) {
        Application.serverApplicationContext = serverWebServerApplicationContext;
    }
}
