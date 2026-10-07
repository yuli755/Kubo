package cn.hamm.spms.common.sms;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsHelperTest {

    @Test
    void rejectsMissingConfiguredProviderInsteadOfFallingBack() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new SmsHelper(List.of(), ""));

        assertEquals("未找到短信提供商: ；生产环境禁止回退到日志短信提供商，请配置真实 SPMS_SMS_PROVIDER", error.getMessage());
    }

    @Test
    void selectsOnlyTheConfiguredProvider() {
        SmsProvider aliyun = new StubProvider("aliyun");
        SmsProvider log = new StubProvider("log");

        SmsHelper helper = new SmsHelper(List.of(log, aliyun), "aliyun");

        assertEquals("aliyun", helper.getActiveProviderName());
    }

    private record StubProvider(String name) implements SmsProvider {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public void sendCode(String phone, String code) {
            // Contract test only; no external delivery.
        }
    }
}
