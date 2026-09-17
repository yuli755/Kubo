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
        // 2026-09-18 后端 airpower 退场【框架层自持】：
        // 原先这里要 exclude cn.hamm.airpower.cookie.Auto（避免它的 ComponentScan 扫出同名
        // cookieHelper / cookieConfig Bean）。现在 airpower 已经不在依赖里，自动装配源也随之消失；
        // 本仓自持层（cn.hamm.spms.core / cn.hamm.spms.framework.*）由下面的组件扫描直接接管，
        // 不再需要任何"排除/注册"的脚手架。
        //
        // 注意：@SpringBootApplication 的默认扫描根是 cn.hamm.spms，**天然覆盖**自持层 ——
        // 所以我们没有沿用 airpower 那套「@AutoConfiguration + META-INF/...AutoConfiguration.imports」
        // 的注册方式（那是给外部 jar 用的；应用内再写一份会与组件扫描重复注册同一个 Bean）。
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
