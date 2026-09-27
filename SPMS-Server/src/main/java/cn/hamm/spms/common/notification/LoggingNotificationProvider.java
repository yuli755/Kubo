package cn.hamm.spms.common.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <h1>日志回退通知提供商（默认安全降级）</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class LoggingNotificationProvider implements NotificationProvider {
    @Override
    public String getName() {
        return "log";
    }

    @Override
    public void sendNotification(String title, String content, String level) {
        log.info("[企业协同告警·{}] title={}, content={}", level, title, content);
    }
}
