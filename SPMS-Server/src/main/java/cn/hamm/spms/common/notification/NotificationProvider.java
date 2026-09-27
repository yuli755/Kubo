package cn.hamm.spms.common.notification;

/**
 * <h1>企业协同与通知服务商接口</h1>
 *
 * @author Hamm.cn
 */
public interface NotificationProvider {
    /**
     * @return 服务商标识（log, wecom, feishu, dingtalk）
     */
    String getName();

    /**
     * 发送企业告警/通知
     *
     * @param title   标题
     * @param content 正文内容
     * @param level   级别 (INFO, WARN, ERROR)
     */
    void sendNotification(String title, String content, String level);
}
