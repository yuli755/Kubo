package cn.hamm.spms.common.sms;

/**
 * <h1>短信服务提供商策略接口</h1>
 *
 * <p>为短信下发（验证码、通知等）提供统一抽象契约，解耦具体云厂商（阿里云 Dysms、腾讯云 SMS、本地日志模拟等）。</p>
 *
 * @author Hamm.cn
 */
public interface SmsProvider {
    /**
     * 服务商标识名称（如 "log"、"aliyun"、"tencent"）
     *
     * @return 服务商标识
     */
    String getName();

    /**
     * 发送短信验证码
     *
     * @param phone 接收手机号码
     * @param code  短信验证码
     */
    void sendCode(String phone, String code);
}
