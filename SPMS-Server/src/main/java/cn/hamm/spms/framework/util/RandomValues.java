package cn.hamm.spms.framework.util;

import java.security.SecureRandom;

/**
 * <h1>密码盐、验证码与 Cookie 随机值</h1>
 *
 * <p>保留原有长度和字符集契约，但这些值用于认证，不能继续使用 ThreadLocalRandom。
 * 不改变密码散列算法、历史盐或缓存键，已有凭据不需要迁移。</p>
 *
 * @author Hamm.cn
 */
public final class RandomValues {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String DIGITS = "0123456789";

    private RandomValues() {
    }

    /** 默认生成 32 位大小写字母与数字。 */
    public static String randomString() {
        return randomString(32);
    }

    /** 保持旧调用约定：非正长度按一位生成。 */
    public static String randomString(int length) {
        return generate(ALPHANUMERIC, length);
    }

    /** 验证码保持字符串形态，因此前导零不会丢失。 */
    public static String randomNumbers(int length) {
        return generate(DIGITS, length);
    }

    private static String generate(String alphabet, int length) {
        char[] value = new char[Math.max(1, length)];
        for (int i = 0; i < value.length; i++) {
            value[i] = alphabet.charAt(RANDOM.nextInt(alphabet.length()));
        }
        return new String(value);
    }
}
