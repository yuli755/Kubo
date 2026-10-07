package cn.hamm.spms.common.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>文件导出配置</h1>
 *
 * <p>替代旧 {@code cn.hamm.airpower.curd.config.ExportConfig}。</p>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("app.export")
public class ExportConfig {
    /**
     * 导出分页大小
     */
    private Integer exportPageSize = 5000;

    /**
     * 导出文件路径
     */
    private String exportPath = "";

    /**
     * 异步导出任务状态在 Redis 中的保留时间（秒）。
     * 任务状态与文件路径都不应永久占用 Redis；正数才启用 TTL。
     */
    private long taskTtlSeconds = 3600L;
}
