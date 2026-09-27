package cn.hamm.spms.module.open.notify;

import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.exception.ServiceException;
import cn.hamm.airpower.email.helper.EmailHelper;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.common.security.WebhookUrlGuard;
import cn.hamm.spms.module.open.notify.enums.NotifyChannel;
import cn.hamm.spms.module.open.notify.enums.NotifyScene;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <h1>Service</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class NotifyService extends BaseService<NotifyEntity, NotifyRepository> {
    /**
     * 线程池
     */
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            5,
            20,
            3600L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>()
    );

    @Autowired
    private EmailHelper emailHelper;

    /**
     * 发送通知
     *
     * @param notifyScene 通知场景
     * @param data        通知数据
     * @param content     通知文案
     */
    public <T> void sendNotification(NotifyScene notifyScene, T data, String content) {
        try {
            EXECUTOR.submit(() -> {
                // 查询指定场景的 Hook 列表
                List<NotifyEntity> notifyList = filter(
                        new NotifyEntity()
                                .setScene(notifyScene.getKey())
                                .setIsDisabled(false)
                );
                final String title = notifyScene.getLabel();
                notifyList.forEach(notify -> {
                    // 获取通知类型
                    NotifyChannel notifyChannel = DictionaryUtil.getDictionary(NotifyChannel.class, notify.getChannel());

                    // 获取各个类型的通知内容(POST结构)
                    String requestData = switch (notifyChannel) {
                        case WORK_WECHAT -> getWorkWechatMarkDown(title, content);
                        case FEI_SHU -> getFeishuMarkDown(title, content);
                        case DING_TALK -> getDingTalkMarkDown(title, content);
                        case EMAIL -> getEmailBody(content);
                        case WEB_HOOK -> getNotifyWebHookBody(notify, data);
                    };

                    // 发起通知
                    doRequest(notify, requestData);
                });
            });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * webhook 专用发送端：跟随重定向等于把 SSRF 校验变成摆设（保存时合法、
     * 一跳 302 进内网），这里显式 NEVER；连接/读取超时兜住慢目标占用工作线程。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 请求
     *
     * @param notify 通知
     * @param data   数据
     */
    private <T> void doRequest(@NotNull NotifyEntity notify, @NotNull T data) {
        NotifyChannel notifyChannel = DictionaryUtil.getDictionary(NotifyChannel.class, notify.getChannel());
        if (notifyChannel == NotifyChannel.EMAIL) {
            // 如果是邮箱通知 直接发送邮件（收件人在发送前再校验一次，兼容改造前的存量行）
            try {
                WebhookUrlGuard.checkEmailRecipient(notify.getUrl());
                NotifyScene scene = DictionaryUtil.getDictionary(NotifyScene.class, notify.getScene());
                emailHelper.sendEmail(notify.getUrl(), scene.getLabel(), data.toString());
            } catch (MessagingException e) {
                log.error(e.getMessage(), e);
            }
            return;
        }

        // 其他通知 发起网络请求
        try {
            WebhookUrlGuard.checkHttpUrl(notify.getUrl());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(notify.getUrl().trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(data.toString()))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                // NEVER 重定向：3xx 不追发，宁可不通知也不给跳转目标开洞
                log.error("通知地址返回重定向（已按 SSRF 策略不跟随）: status={}", response.statusCode());
            }
        } catch (ServiceException e) {
            log.error("通知被 SSRF 守卫拦截: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 保存前校验（fail-fast）：URL 类渠道必须是公网 http(s)，EMAIL 渠道必须是单收件人。
     * 发送路径（doRequest）还有第二道，兼容改造前的存量行。
     */
    @Override
    protected @NotNull NotifyEntity beforeAdd(@NotNull NotifyEntity entity) {
        validateTarget(entity.getChannel(), entity.getUrl());
        return super.beforeAdd(entity);
    }

    @Override
    protected @NotNull NotifyEntity beforeUpdate(@NotNull NotifyEntity entity) {
        if (entity.getUrl() != null && !entity.getUrl().isBlank()) {
            Integer channel = entity.getChannel();
            if (channel == null && entity.getId() != null) {
                NotifyEntity exist = get(entity.getId());
                channel = exist == null ? null : exist.getChannel();
            }
            validateTarget(channel, entity.getUrl());
        }
        return super.beforeUpdate(entity);
    }

    private void validateTarget(Integer channel, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (NotifyChannel.EMAIL.getKey() == (channel == null ? NotifyChannel.WORK_WECHAT.getKey() : channel)) {
            WebhookUrlGuard.checkEmailRecipient(url);
        } else {
            WebhookUrlGuard.checkHttpUrl(url);
        }
    }

    /**
     * 获取企业微信 MarkDown 格式
     *
     * @param title   通知标题
     * @param content 通知内容
     * @return 企业微信 MarkDown
     */
    protected final String getWorkWechatMarkDown(String title, String content) {
        return Json.toString(Map.of(
                "msgtype", "markdown",
                "markdown", Map.of(
                        "content", String.format("# %s\n\n%s", title, content)
                )
        ));
    }

    /**
     * 获取钉钉 MarkDown 格式
     *
     * @param title   通知标题
     * @param content 通知内容
     * @return 钉钉 MarkDown
     */
    protected final String getDingTalkMarkDown(String title, String content) {
        return Json.toString(Map.of(
                "msgtype", "markdown",
                "markdown", Map.of(
                        "text", String.format("# %s\n\n%s", title, content),
                        "title", title
                )
        ));
    }

    /**
     * 获取飞书 MarkDown 格式
     *
     * @param title   通知标题
     * @param content 通知内容
     * @return 飞书 MarkDown
     */
    protected final String getFeishuMarkDown(String title, String content) {
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(Map.of(
                "tag", "div",
                "text", Map.of(
                        "tag", "lark_md",
                        "content", String.format("# %s\n\n%s", title, content)
                )
        ));
        return Json.toString(Map.of(
                "msg_type", "interactive",
                "card", Map.of(
                        "elements", elements
                ),
                "header", Map.of(
                        "title", Map.of(
                                "tag", "plain_text",
                                "content", content
                        )
                )
        ));
    }

    /**
     * 获取邮件内容
     *
     * @param content 通知内容
     * @return 邮件内容
     */
    @Contract(pure = true)
    protected final @NotNull String getEmailBody(@NotNull String content) {
        return content.replace("\n", "<br/>");
    }

    /**
     * 获取通知 WebHook 包体
     *
     * @param notify 通知
     * @return 通知包体
     */
    protected final <T> String getNotifyWebHookBody(@NotNull NotifyEntity notify, T data) {
        NotifyScene scene = DictionaryUtil.getDictionary(NotifyScene.class, notify.getScene());
        return Json.toString(Map.of(
                "scene", scene.name(),
                "remark", notify.getRemark(),
                "token", notify.getToken(),
                "data", data));
    }
}
