package cn.hamm.spms.framework.security;

import cn.hamm.spms.common.exception.Errors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <h1>用户密码加盐散列（与历史库中凭据兼容）</h1>
 *
 * <p>完全兼容 AirPower 的 {@code PermissionUtil.encodePassword(password, salt)} 算法：
 * {@code sha1Hex(sha1Hex(password + salt) + sha1Hex(salt + password))}。
 * 纯 JDK 17 实现，无任何第三方依赖；不使用默认时区或语言环境。
 * 密码或盐为空时抛出统一业务异常 {@link Errors#PARAM_MISSING}。</p>
 *
 * @author Hamm.cn
 */
public final class PasswordHasher {
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private PasswordHasher() {
    }

    /**
     * 密码加盐散列（双向拼接 SHA-1 摘要后再做一次 SHA-1）
     *
     * @param password 明文密码
     * @param salt     盐
     * @return 40 位小写十六进制散列
     */
    public static String encode(String password, String salt) {
        Errors.PARAM_MISSING.whenEmpty(password, "密码不能为空");
        Errors.PARAM_MISSING.whenEmpty(salt, "盐不能为空");
        String p1 = sha1Hex(password + salt);
        String p2 = sha1Hex(salt + password);
        return sha1Hex(p1 + p2);
    }

    private static String sha1Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            char[] chars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xFF;
                chars[i * 2] = HEX_CHARS[v >>> 4];
                chars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 摘要算法不可用", e);
        }
    }
}
