package cn.hamm.spms.common.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import static cn.hamm.airpower.exception.Errors.EMAIL_ERROR;

/**
 * <h1>邮件发送助手（自建，取代 airpower-email）</h1>
 *
 * <p>
 * 2026-09-18 后端 airpower 退场第一批：本类是从 {@code cn.hamm.airpower.email.helper.EmailHelper}
 * 逐行搬过来的自建实现，对外行为<b>完全一致</b>（同样的两个方法、同样的 HTML 模板、
 * 同样的 {@link MessagingException} 抛出语义、同样的发件人取自 {@code spring.mail.username}）。
 * 搬过来的动机是摘掉 {@code airpower-email} 这个依赖 —— 全仓只有 {@code UserService} 用它的
 * 发验证码能力，为它保留一个框架模块不划算。
 * </p>
 *
 * <p>
 * <b>接口契约</b>：
 * </p>
 * <ul>
 *   <li>{@code JavaMailSender} 是 <b>optional</b> 注入（{@code required = false}）：未配置
 *       {@code spring.mail.host} 时 Spring Boot 不会创建该 Bean，此时发信会抛出「未配置邮件服务」
 *       的框架错误码（{@code EMAIL_ERROR, 5023}）—— 与旧实现同一行为，未配置=明确失败，不静默丢信。</li>
 *   <li>发件人取 {@code spring.mail.username}（与 SMTP 账号同源）。</li>
 * </ul>
 *
 * <p>
 * <b>仍存的耦合（下一批处理）</b>：这里还引用 airpower 的 {@code Errors.EMAIL_ERROR}。
 * 错误码层（{@code Errors}/{@code Json.error}/{@code ServiceException} 共 55 处）是独立的
 * 一批改动，届时本行改为项目自有错误码，其余不动。
 * </p>
 *
 * @author Hamm.cn
 */
@Component
public class EmailHelper {
    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    /**
     * 发件人/用户名
     */
    @Value("${spring.mail.username:}")
    private String mailFrom;

    /**
     * 发送邮件验证码
     *
     * @param email 邮箱
     * @param title 标题
     * @param code  验证码
     * @param sign  签名
     */
    public final void sendCode(@NotNull String email,
                               @NotNull String title,
                               @NotNull String code,
                               @NotNull String sign
    ) throws MessagingException {
        String content = """
                <div style='border-radius:20px;padding: 20px;background-color:#f5f5f5;color:#333;display:inline-block;'>
                <div style='font-size:24px;font-weight:bold;color:orangered;'>
                """ + code + """
                </div>
                <div style='margin-top:20px;font-size:16px;font-weight:300'>
                    上面是你的验证码，请注意不要转发给他人，五分钟内有效，请尽快使用。
                </div>
                <div style='margin-top:10px;font-size:12px;color:#aaa;font-weight:300'>
                """ + sign + """
                </div></div>
                """;
        sendEmail(email, title, content);
    }

    /**
     * 发送邮件
     *
     * @param email   邮箱
     * @param title   标题
     * @param content 内容
     */
    public final void sendEmail(
            @NotNull String email, @NotNull String title, @NotNull String content
    ) throws MessagingException {
        EMAIL_ERROR.whenNull(javaMailSender, "未配置邮件服务的信息(spring.mail.username)");
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(email);
        helper.setSubject(title);
        helper.setFrom(mailFrom);
        helper.setText(content, true);
        javaMailSender.send(message);
    }
}
