package cn.hamm.spms.common.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * <h1>阿里云短信通道配置属性</h1>
 *
 * <p>凭证通过环境变量注入，禁止硬编码进源码仓库：
 * <ul>
 *   <li>{@code ALIBABA_CLOUD_ACCESS_KEY_ID}</li>
 *   <li>{@code ALIBABA_CLOUD_ACCESS_KEY_SECRET}</li>
 *   <li>{@code SPMS_SMS_ALIYUN_SIGN_NAME}</li>
 *   <li>{@code SPMS_SMS_ALIYUN_TEMPLATE_CODE}</li>
 * </ul>
 * </p>
 *
 * @author Hamm.cn
 */
@Data
@Component
@ConfigurationProperties(prefix = "spms.sms.aliyun")
public class AliyunSmsProperties {
    /**
     * 阿里云 AccessKey ID
     */
    private String accessKeyId = "";

    /**
     * 阿里云 AccessKey Secret
     */
    private String accessKeySecret = "";

    /**
     * 短信签名名称
     */
    private String signName = "";

    /**
     * 短信模板 CODE（如 SMS_123456789）
     */
    private String templateCode = "";

    /**
     * POP API 服务接入点
     */
    private String endpoint = "dysmsapi.aliyuncs.com";

    /**
     * HTTP 连接与响应超时时间（毫秒）
     */
    private int timeoutMs = 5000;
}
