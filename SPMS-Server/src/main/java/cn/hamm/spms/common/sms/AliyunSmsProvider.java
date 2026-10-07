package cn.hamm.spms.common.sms;

import cn.hamm.spms.framework.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.hamm.spms.common.exception.Errors.SERVICE_ERROR;

/**
 * <h1>阿里云短信服务提供商（Dysmsapi POP 协议自持实现）</h1>
 *
 * <p>基于 JDK 17 原生 {@link HttpClient} 与标准 POP HMAC-SHA1 签名算法，
 * 零引入庞大阿里云外部 SDK（减小包体积并规避依赖冲突）。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spms.sms.provider", havingValue = "aliyun")
public class AliyunSmsProvider implements SmsProvider {
    private static final String ALGORITHM = "HmacSHA1";
    private static final String ACTION = "SendSms";
    private static final String VERSION = "2017-05-25";
    private static final String FORMAT = "JSON";
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String SIGNATURE_VERSION = "1.0";
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private final AliyunSmsProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public AliyunSmsProvider(AliyunSmsProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    @Override
    public String getName() {
        return "aliyun";
    }

    @Override
    public void sendCode(String phone, String code) {
        if (properties.getAccessKeyId().isBlank() || properties.getAccessKeySecret().isBlank()) {
            log.error("【阿里云短信】未配置 AccessKey 凭证，无法下发真实短信");
            throw new BusinessException(SERVICE_ERROR, "短信服务未正确配置凭证");
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("AccessKeyId", properties.getAccessKeyId());
            params.put("Action", ACTION);
            params.put("Format", FORMAT);
            params.put("PhoneNumbers", phone);
            params.put("SignName", properties.getSignName());
            params.put("TemplateCode", properties.getTemplateCode());
            params.put("TemplateParam", String.format("{\"code\":\"%s\"}", code));
            params.put("SignatureMethod", SIGNATURE_METHOD);
            params.put("SignatureNonce", UUID.randomUUID().toString());
            params.put("SignatureVersion", SIGNATURE_VERSION);
            params.put("Timestamp", UTC_FORMATTER.format(Instant.now()));
            params.put("Version", VERSION);

            // 1. 参数排序与规范化拼接
            List<String> sortedKeys = new ArrayList<>(params.keySet());
            Collections.sort(sortedKeys);
            StringBuilder canonicalizedQueryString = new StringBuilder();
            for (String key : sortedKeys) {
                if (!canonicalizedQueryString.isEmpty()) {
                    canonicalizedQueryString.append('&');
                }
                canonicalizedQueryString.append(percentEncode(key))
                        .append('=')
                        .append(percentEncode(params.get(key)));
            }

            // 2. 构造待签名字符串
            String stringToSign = "POST" + "&" +
                    percentEncode("/") + "&" +
                    percentEncode(canonicalizedQueryString.toString());

            // 3. 计算 HMAC-SHA1 签名
            String signature = calculateHmacSha1(stringToSign, properties.getAccessKeySecret() + "&");

            // 4. 发送 HTTP POST 请求
            String requestBody = canonicalizedQueryString + "&Signature=" + percentEncode(signature);
            String url = "https://" + properties.getEndpoint() + "/";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body();

            if (response.statusCode() == 200 && responseBody != null && responseBody.contains("\"Code\":\"OK\"")) {
                log.info("【阿里云短信】验证码发送成功：phone={}, template={}", SmsPhoneMasker.mask(phone), properties.getTemplateCode());
            } else {
                log.error("【阿里云短信】发送失败：httpStatus={}, body={}", response.statusCode(), responseBody);
                throw new BusinessException(SERVICE_ERROR, "短信发送失败：" + (responseBody != null ? responseBody : "服务无响应"));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("【阿里云短信】调用异常：phone={}, error={}", SmsPhoneMasker.mask(phone), e.getMessage(), e);
            throw new BusinessException(SERVICE_ERROR, "短信发送服务不可用");
        }
    }

    /**
     * 阿里云 POP 规范特殊 URL 编码
     */
    public static String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private static String calculateHmacSha1(String data, String key) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        byte[] signData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }
}
