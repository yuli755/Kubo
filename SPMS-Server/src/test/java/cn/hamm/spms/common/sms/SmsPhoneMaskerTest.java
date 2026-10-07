package cn.hamm.spms.common.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmsPhoneMaskerTest {

    @Test
    void masksStandardPhoneNumbers() {
        assertEquals("138****5678", SmsPhoneMasker.mask("13812345678"));
        assertEquals("+86****5678", SmsPhoneMasker.mask("+8613812345678"));
    }

    @Test
    void masksShortAndBlankValues() {
        assertEquals("****", SmsPhoneMasker.mask("1234"));
        assertEquals("1****7", SmsPhoneMasker.mask("1234567"));
        assertEquals("<empty>", SmsPhoneMasker.mask("  "));
        assertEquals("<empty>", SmsPhoneMasker.mask(null));
    }
}
