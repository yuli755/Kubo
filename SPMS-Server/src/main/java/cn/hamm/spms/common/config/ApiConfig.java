package cn.hamm.spms.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * <h1>API 配置属性</h1>
 *
 * <p>替代旧 {@code cn.hamm.airpower.api.config.ApiConfig}。</p>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.api")
public class ApiConfig {
    public static boolean isServerRunning = true;
    private Boolean requestLog = true;
    private Boolean responseLog = true;
    private Boolean bodyTraceId = true;
    private String accessTokenSecret = "1234567890";
    private String authorizeHeader = HttpHeaders.AUTHORIZATION;
}
