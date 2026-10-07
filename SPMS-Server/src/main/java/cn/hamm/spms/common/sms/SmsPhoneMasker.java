package cn.hamm.spms.common.sms;

/**
 * 短信日志中的手机号脱敏工具；日志不得成为 OTP/PII 的第二个存储面。
 */
public final class SmsPhoneMasker {
    private SmsPhoneMasker() {
    }

    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return "<empty>";
        }
        String value = phone.trim();
        if (value.length() <= 4) {
            return "****";
        }
        if (value.length() <= 7) {
            return value.substring(0, 1) + "****" + value.substring(value.length() - 1);
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
}
