package cn.hamm.spms.common.influx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>InfluxDB 配置</h1>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("app.influxdb")
public class InfluxConfig {
    /**
     * 连接地址
     */
    private String url = "http://127.0.0.1:8086";

    /**
     * 连接令牌
     *
     * <p><b>安全（S2）</b>：默认值留空，不再在源码里含真实令牌。运行时通过配置
     * {@code app.influxdb.token} 或环境变量 {@code APP_INFLUXDB_TOKEN} 注入；
     * 未配置时 InfluxDB 上报静默禁用（见 {@link InfluxHelper}）。</p>
     */
    private String token = "";

    /**
     * 使用的组织名
     */
    private String org = "influx";

    /**
     * 使用的数据库
     */
    private String bucket = "spms";
}
