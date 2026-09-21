package cn.hamm.spms.common.security;

import cn.hamm.spms.common.exception.Errors;
import cn.hamm.spms.framework.error.BusinessException;
import cn.hamm.spms.framework.json.JsonCodec;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <h1>访问令牌工具</h1>
 *
 * <p>兼容 AirPower 规范的 HMAC-SHA256 Token 签发与验证工具。
 * 替代旧 {@code cn.hamm.airpower.core.AccessTokenUtil}。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
public class AccessTokenUtil {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_DELIMITER = ".";
    private static final String TOKEN_DELIMITER_SPLIT = "\\.";
    private static final int UNAUTHORIZED_CODE = 401;

    private final VerifiedToken verifiedToken = new VerifiedToken();

    private AccessTokenUtil() {
    }

    public static AccessTokenUtil create() {
        return new AccessTokenUtil();
    }

    private static void fail(String message) {
        throw new BusinessException(UNAUTHORIZED_CODE, message, null);
    }

    private static boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public final String build(String secret) {
        if (!hasText(secret)) {
            fail("构建 Token 失败，密钥不能为空");
        }
        if (verifiedToken.getPayloads().isEmpty()) {
            fail("构建 Token 失败，负载数据不能为空");
        }
        String payloadBase = Base64.getUrlEncoder().encodeToString(
                JsonCodec.write(verifiedToken.getPayloads()).getBytes(UTF_8)
        );
        return verifiedToken.getExpireTimestamps() +
                TOKEN_DELIMITER +
                payloadBase +
                TOKEN_DELIMITER +
                hmacSha256(secret, verifiedToken.getExpireTimestamps() + TOKEN_DELIMITER + payloadBase);
    }

    public final AccessTokenUtil setPayload(String key, Object value) {
        verifiedToken.getPayloads().put(key, value);
        return this;
    }

    public final AccessTokenUtil removePayload(String key) {
        verifiedToken.getPayloads().remove(key);
        return this;
    }

    public final AccessTokenUtil setPayloads(Map<String, Object> payloads) {
        verifiedToken.setPayloads(payloads != null ? payloads : new HashMap<>());
        return this;
    }

    public final AccessTokenUtil setExpire(long millisecond) {
        verifiedToken.setExpireTimestamps(System.currentTimeMillis() + millisecond);
        return this;
    }

    public final AccessTokenUtil setExpireTimestamps(long expireTimestamps) {
        verifiedToken.setExpireTimestamps(expireTimestamps);
        return this;
    }

    @SuppressWarnings("unchecked")
    public final VerifiedToken verify(String accessToken, String secret) {
        if (!hasText(secret)) {
            fail("验证 Token 失败，密钥不能为空");
        }
        if (!hasText(accessToken)) {
            fail("令牌不能为空");
        }
        String[] strings = accessToken.split(TOKEN_DELIMITER_SPLIT);
        if (strings.length != 3) {
            fail("身份验证失败，请重新登录");
        }
        String expireString = strings[0];
        String payloadString = strings[1];
        String signString = strings[2];
        String source = expireString + TOKEN_DELIMITER + payloadString;
        if (!hasText(source)) {
            fail("身份验证失败，请重新登录");
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(expireString);
        } catch (NumberFormatException e) {
            fail("身份验证失败，请重新登录");
            return null;
        }
        if (expireAt < System.currentTimeMillis()) {
            fail("登录已过期，请重新登录");
        }
        if (!signString.equalsIgnoreCase(hmacSha256(secret, source))) {
            fail("身份验证失败，请重新登录");
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(payloadString), UTF_8);
        Map<String, Object> payloads = (Map<String, Object>) JsonCodec.read(payloadJson, Map.class);
        return new VerifiedToken()
                .setExpireTimestamps(expireAt)
                .setPayloads(payloads != null ? payloads : new HashMap<>());
    }

    private String hmacSha256(String secret, String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), HMAC_SHA256));
            byte[] signData = mac.doFinal(content.getBytes(UTF_8));
            return Base64.getUrlEncoder().encodeToString(signData);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC-SHA-256 签名计算异常", e);
            fail("签名计算失败");
            return "";
        }
    }

    /**
     * 已验证的 Token 数据载体
     */
    public static class VerifiedToken {
        private long expireTimestamps;
        private Map<String, Object> payloads = new HashMap<>();

        public long getExpireTimestamps() {
            return expireTimestamps;
        }

        public VerifiedToken setExpireTimestamps(long expireTimestamps) {
            this.expireTimestamps = expireTimestamps;
            return this;
        }

        public Map<String, Object> getPayloads() {
            return payloads;
        }

        public VerifiedToken setPayloads(Map<String, Object> payloads) {
            this.payloads = payloads != null ? payloads : new HashMap<>();
            return this;
        }

        public Object getPayload(String key) {
            return payloads.get(key);
        }

        public long getPayloadId() {
            Object id = getPayload("id");
            if (id instanceof Number num) {
                return num.longValue();
            }
            if (id != null) {
                try {
                    return Long.parseLong(id.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            return 0L;
        }
    }
}
