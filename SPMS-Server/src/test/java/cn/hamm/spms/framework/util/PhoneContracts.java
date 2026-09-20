package cn.hamm.spms.framework.util;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import static cn.hamm.spms.framework.util.UtilityContracts.check;
import static cn.hamm.spms.framework.util.UtilityContracts.equal;

/**
 * <h1>生产 Pattern 常量的独立契约</h1>
 *
 * <p>nullable/group/message 的 Jakarta 接线另测，这里不冒充验证提供者。
 * 参考式固定为 core 6.3.1 的两个表达式，生成数据对照 OR/空串语义，不加载旧类。</p>
 *
 * @author Hamm.cn
 */
final class PhoneContracts {
    private static final Pattern ACTUAL = Pattern.compile(PhoneRules.OPTIONAL_PHONE);
    private static final Pattern REFERENCE_MOBILE = Pattern.compile("^(\\+(\\d{1,4}))?1[3-9](\\d{9})$");
    private static final Pattern REFERENCE_TEL = Pattern.compile("^(((0\\d{2,3})-)?((\\d{7,8})|(400\\d{7})|(800\\d{7}))(-(\\d{1,4}))?)$");

    private PhoneContracts() {
    }

    static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("phone/optional-empty-not-blank", () -> {
                    check(valid(""));
                    for (String blank : List.of(" ", "\t", "\n", "\r\n", "\u3000")) {
                        check(!valid(blank));
                    }
                }),
                new UtilityContracts.Case("phone/mobile-prefixes", () -> {
                    for (int prefix = 3; prefix <= 9; prefix++) {
                        check(valid("1" + prefix + "800138000"));
                    }
                    check(!valid("12800138000"));
                    check(!valid("1380013800"));
                    check(!valid("138001380000"));
                }),
                new UtilityContracts.Case("phone/country-code-lengths", () -> {
                    for (String code : List.of("+1", "+86", "+123", "+1234")) {
                        check(valid(code + "13800138000"));
                    }
                    check(!valid("+1234513800138000"));
                    check(!valid("+13800138000"));
                    check(!valid("8613800138000"));
                }),
                new UtilityContracts.Case("phone/landline-local-lengths", () -> {
                    check(valid("1234567"));
                    check(valid("12345678"));
                    check(!valid("123456"));
                    check(!valid("123456789"));
                }),
                new UtilityContracts.Case("phone/area-code-hyphen", () -> {
                    check(valid("010-12345678"));
                    check(valid("0755-1234567"));
                    check(!valid("10-12345678"));
                    check(!valid("010012345678"));
                    check(!valid("(010)12345678"));
                }),
                new UtilityContracts.Case("phone/service-numbers", () -> {
                    check(valid("4001234567"));
                    check(valid("8001234567"));
                    check(valid("010-4001234567-1234"));
                    check(!valid("9001234567"));
                    check(!valid("400-123-4567"));
                }),
                new UtilityContracts.Case("phone/extensions", () -> {
                    for (String ext : List.of("1", "12", "123", "1234")) {
                        check(valid("010-12345678-" + ext));
                    }
                    check(!valid("010-12345678-"));
                    check(!valid("010-12345678-12345"));
                    check(!valid("13800138000-1"));
                }),
                new UtilityContracts.Case("phone/no-trimming-or-newline-acceptance", () -> {
                    for (String extra : List.of(" ", "\t", "\n", "\r", "\u3000")) {
                        check(!valid(extra + "13800138000"));
                        check(!valid("13800138000" + extra));
                    }
                }),
                new UtilityContracts.Case("phone/ascii-digits-only", () -> {
                    check(!valid("１３８００１３８０００"));
                    check(!valid("١٣٨٠٠١٣٨٠٠٠"));
                    check(!valid("+８６13800138000"));
                }),
                new UtilityContracts.Case("phone/no-substring-matching", () -> {
                    for (String invalid : List.of("phone=13800138000", "13800138000,13900139000", "javascript:12345678", "13800138000\u0000", "-12345678")) {
                        check(!valid(invalid));
                        check(!ACTUAL.matcher(invalid).find());
                    }
                }),
                new UtilityContracts.Case("phone/generated-compatibility", PhoneContracts::generatedParity),
                new UtilityContracts.Case("phone/error-message-constant", () -> equal("不是有效的电话号码", PhoneRules.INVALID_PHONE_MESSAGE))
        );
    }

    private static boolean valid(String value) {
        return ACTUAL.matcher(value).matches();
    }

    private static void sameAsReference(String value) {
        equal(value.isEmpty() || REFERENCE_MOBILE.matcher(value).matches() || REFERENCE_TEL.matcher(value).matches(), valid(value));
    }

    private static void generatedParity() {
        // 固定种子只用于构造测试数据，绝不用于认证材料。
        Random random = new Random(20260920L);
        String alphabet = "0123456789+- ()abc\r\n\t";
        for (int i = 0; i < 3000; i++) {
            StringBuilder input = new StringBuilder();
            for (int j = random.nextInt(32); j > 0; j--) {
                input.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            sameAsReference(input.toString());
        }
        for (String area : List.of("", "010-", "0755-", "01-", "01234-")) {
            for (String ext : List.of("", "-1", "-1234", "-12345")) {
                for (String number : List.of("1234567", "12345678", "4001234567", "8001234567", "123456789")) {
                    sameAsReference(area + number + ext);
                }
            }
        }
    }
}
