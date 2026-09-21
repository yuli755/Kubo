package cn.hamm.spms.common.sms;

import cn.hamm.spms.framework.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;
import static cn.hamm.spms.common.exception.Errors.SERVICE_ERROR;

/**
 * <h1>短信发送助手（策略驱动、解耦服务商）</h1>
 *
 * <p>提供前置参数校验、服务商策略分发、异常捕获与发送审计保护。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class SmsHelper {
    private final SmsProvider activeProvider;

    @Autowired
    public SmsHelper(List<SmsProvider> providers) {
        if (providers != null && !providers.isEmpty()) {
            this.activeProvider = providers.get(0);
        } else {
            this.activeProvider = new LoggingSmsProvider();
        }
        log.info("SmsHelper 初始化就绪，当前激活的短信提供商为 [{}]", activeProvider.getName());
    }

    /**
     * 发送短信验证码
     *
     * @param phone 目标手机号
     * @param code  6 位验证码
     */
    public void sendCode(@NotNull String phone, @NotNull String code) {
        PARAM_INVALID.when(Objects.isNull(phone) || phone.isBlank(), "手机号码不能为空");
        PARAM_INVALID.when(Objects.isNull(code) || code.isBlank(), "短信验证码不能为空");

        try {
            activeProvider.sendCode(phone, code);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信发送异常 [provider={}]: phone={}, error={}", activeProvider.getName(), phone, e.getMessage(), e);
            throw new BusinessException(SERVICE_ERROR, "短信发送失败，请稍后重试");
        }
    }

    /**
     * @return 当前激活的服务商标识
     */
    public String getActiveProviderName() {
        return activeProvider.getName();
    }
}
