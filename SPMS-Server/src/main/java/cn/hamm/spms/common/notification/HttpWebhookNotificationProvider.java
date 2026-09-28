package cn.hamm.spms.common.notification;

import cn.hamm.spms.common.security.WebhookUrlGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * <h1>企业协同机器人 Webhook 通知提供商</h1>
 *
 * <p>支持向企业微信、飞书、钉钉等群机器人推送富文本/Markdown 告警卡片，支持异步非阻塞执行与多平台格式化。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class HttpWebhookNotificationProvider implements NotificationProvider {

    @Value("${spms.notification.webhook.url:}")
    private String webhookUrl;

    @Value("${spms.notification.webhook.platform:generic}")
    private String platform;

    @Override
    public String getName() {
        return "webhook";
    }

    @Override
    public void sendNotification(String title, String content, String level) {
        if (!StringUtils.hasText(webhookUrl)) {
            log.debug("[企业通知·Webhook未配置] 跳过网络投递: level={}, title={}, content={}", level, title, content);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String payload = formatPayload(title, content, level);
                postJson(webhookUrl, payload);
                log.info("[企业通知·Webhook投递成功] platform={}, title={}", platform, title);
            } catch (Exception e) {
                log.error("[企业通知·Webhook投递失败] url={}, title={}, error={}", webhookUrl, title, e.getMessage());
            }
        });
    }

    public String formatPayload(String title, String content, String level) {
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String safeContent = content != null ? content.replace("\"", "\\\"").replace("\n", "\\n") : "";
        String safeTitle = title != null ? title.replace("\"", "\\\"") : "";

        if ("wecom".equalsIgnoreCase(platform)) {
            String md = String.format("### %s\\n> **告警级别**: <font color=\\\"%s\\\">%s</font>\\n> **事件内容**: %s\\n> **发生时间**: %s",
                    safeTitle,
                    "ERROR".equalsIgnoreCase(level) ? "warning" : "info",
                    level,
                    safeContent,
                    nowStr
            );
            return String.format("{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\"%s\"}}", md);
        } else if ("feishu".equalsIgnoreCase(platform)) {
            String text = String.format("【%s】%s\\n%s\\n时间: %s", level, safeTitle, safeContent, nowStr);
            return String.format("{\"msg_type\":\"text\",\"content\":{\"text\":\"%s\"}}", text);
        } else if ("dingtalk".equalsIgnoreCase(platform)) {
            String text = String.format("### %s\\n- **级别**: %s\\n- **内容**: %s\\n- **时间**: %s", safeTitle, level, safeContent, nowStr);
            return String.format("{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"%s\",\"text\":\"%s\"}}", safeTitle, text);
        } else {
            return String.format("{\"title\":\"%s\",\"level\":\"%s\",\"content\":\"%s\",\"time\":\"%s\"}",
                    safeTitle, level, safeContent, nowStr);
        }
    }

    private void postJson(String targetUrl, String json) throws Exception {
        // SSRF 防护：webhookUrl 来自配置项，等价于把「服务端向任意地址发请求」的能力
        // 交给持有配置权的人。出站前收敛到公网 http(s)，拒绝内网/环回/链路本地
        // （169.254 = 云 metadata）/组播/CGNAT/IPv6 ULA。
        WebhookUrlGuard.checkHttpUrl(targetUrl);
        URL url = new URL(targetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            log.warn("[企业通知·Webhook返回非2xx] responseCode={}", code);
        }
    }
}
