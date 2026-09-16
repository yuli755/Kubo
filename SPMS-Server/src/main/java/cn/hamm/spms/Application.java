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
@SpringBootApplication
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
