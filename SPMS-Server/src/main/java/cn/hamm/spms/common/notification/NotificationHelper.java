package cn.hamm.spms.common.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <h1>企业协同与告警通知助手（策略驱动、解耦服务商）</h1>
 *
 * <p>支持将单据审核驳回、权限死信告警、异常事件推送至企业协同通道（飞书/企微/钉钉），未配置时自动安全降级至日志。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class NotificationHelper {
    private final NotificationProvider activeProvider;

    @Autowired
    public NotificationHelper(List<NotificationProvider> providers,
                              @Value("${spms.notification.provider:log}") String targetProvider) {
        NotificationProvider selected = null;
        if (providers != null) {
            for (NotificationProvider provider : providers) {
                if (provider.getName().equalsIgnoreCase(targetProvider)) {
                    selected = provider;
                    break;
                }
            }
            if (selected == null && !providers.isEmpty()) {
                selected = providers.get(0);
            }
        }
        if (selected == null) {
            selected = new LoggingNotificationProvider();
        }
        this.activeProvider = selected;
        log.info("NotificationHelper 初始化就绪，当前激活通知服务商为 [{}]", activeProvider.getName());
    }

    public void sendInfo(String title, String content) {
        activeProvider.sendNotification(title, content, "INFO");
    }

    public void sendWarning(String title, String content) {
        activeProvider.sendNotification(title, content, "WARN");
    }

    public void sendError(String title, String content) {
        activeProvider.sendNotification(title, content, "ERROR");
    }

    public String getActiveProviderName() {
        return activeProvider.getName();
    }
}
