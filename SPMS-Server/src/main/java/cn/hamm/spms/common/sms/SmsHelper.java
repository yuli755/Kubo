package cn.hamm.spms.common.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <h1>短信发送助手</h1>
 * <p>
 * 与 {@code cn.hamm.spms.common.email.EmailHelper}（已接入、可真实发邮件）对位：邮箱验证码已闭环，
 * 短信验证码此前只走到「生成验证码 + 存 Redis」，最后一步「真发短信」停在这里（原代码是
 * {@code log.info("短信验证码：{}", code)} + {@code //todo 发送验证码}）。
 * </p>
 * <p>
 * 本类把「发送」这一步收敛成一个可替换的助手类：当前为<b>占位实现（仅日志）</b>，接入真实服务商时
 * 只需改本类的 {@link #sendCode(String, String)}（调用方 {@code UserService} 无需改动）：
 * </p>
 * <ol>
 *   <li>引入服务商 SDK（如 {@code aliyun-sdk-dysmsapi}）或直接调 HTTP 接口；</li>
 *   <li>短信签名 / 模板 ID 走 {@code application.yml} 外置配置（与 S2 凭据外置同口径，不硬编码）；</li>
 *   <li>accessKey/secret 走环境变量注入，禁止写进仓库。</li>
 * </ol>
 * <p>
 * 之所以不直接写死某个服务商：短信服务商（阿里云/腾讯云/华为云…）与签名/模板均属企业账号资源，
 * 由真实环境决定，此处不臆测偏好。
 * </p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class SmsHelper {
    /**
     * 发送短信验证码
     *
     * @param phone 手机号
     * @param code  验证码（6 位数字，已存 Redis 供校验，有效期见 {@code UserService.CACHE_CODE_EXPIRE_SECOND}）
     */
    public void sendCode(String phone, String code) {
        // 占位实现：未接入真实短信通道时仅打印到日志（验证码仍存 Redis，可人工查询）。
        // 接入真实服务商后，这里替换为调用 SDK 发送；失败应抛业务异常，让 sendSmsCode 的事务语义
        // 决定是否回滚（当前 sendSmsCode 无事务，验证码已落 Redis，发送失败不影响校验侧）。
        log.info("短信验证码：{}（手机号 {}，未接入真实短信通道，仅日志）", code, phone);
    }
}
