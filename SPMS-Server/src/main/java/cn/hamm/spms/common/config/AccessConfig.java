package cn.hamm.spms.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>访问与权限配置属性</h1>
 *
 * <p>替代旧 {@code cn.hamm.airpower.curd.config.AccessConfig}。</p>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties("airpower.curd.access")
public class AccessConfig {
    /**
     * 身份令牌有效期（秒），默认 86400（1 天）
     */
    private long authorizeExpireSecond = 86400L;
}
