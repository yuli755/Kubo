package cn.hamm.spms.common.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * <h1>日志占位短信提供商（开发与测试环境默认）</h1>
 *
 * <p>未配置真实短信服务商时默认启用，仅在非生产环境输出脱敏手机号提示，绝不记录验证码。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@Profile("!production")
@ConditionalOnProperty(name = "spms.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsProvider implements SmsProvider {

    @Override
    public String getName() {
        return "log";
    }

    @Override
    public void sendCode(String phone, String code) {
        log.info("【短信通道·模拟】向手机号 [{}] 发送验证码（已隐藏，5分钟内有效）", SmsPhoneMasker.mask(phone));
    }
}
