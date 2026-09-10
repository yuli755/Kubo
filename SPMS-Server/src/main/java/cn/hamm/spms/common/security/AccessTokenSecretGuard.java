package cn.hamm.spms.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * <h1>生产环境 JWT 签名密钥强度守卫</h1>
 *
 * <p>密钥卫生是「密钥轮换」的前置条件：轮换的前提是有一个可轮换的、真正随机的密钥。
 * airpower 的 {@code access-token-secret} 默认值是弱密钥 {@code 1234567890}（见
 * {@code application.yml}），一旦生产忘了覆盖环境变量，等于把 JWT 签名密钥写死在源码里——
 * 任何人都能伪造任意身份令牌。</p>
 *
 * <p>本守卫在生产 profile 启动时做 fail-fast 校验：</p>
 * <ul>
 *   <li>密钥为空 / 等于默认值 {@value #WEAK_DEFAULT} → 直接抛异常拒绝启动；</li>
 *   <li>密钥长度不足 {@value #MIN_SECRET_LENGTH} 位 → 抛异常拒绝启动（HS256 建议 ≥ 256 位熵）。</li>
 * </ul>
 *
 * <p>仅在 {@code production} profile 生效；{@code local}/{@code test} 用弱默认值无妨，不拦。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@Profile("production")
public class AccessTokenSecretGuard implements ApplicationRunner {

    /** airpower 的弱默认密钥（写死在 application.yml 的 fallback） */
    private static final String WEAK_DEFAULT = "1234567890";
    /** HS256 建议最小密钥长度（字符，≥ 256 位熵） */
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${airpower.api.access-token-secret:}")
    private String accessTokenSecret;

    @Override
    public void run(ApplicationArguments args) {
        validate(accessTokenSecret);
        log.info("JWT 签名密钥已就绪（长度 {}），生产未使用默认弱密钥", accessTokenSecret.length());
    }

    /**
     * 校验密钥强度（抽出为静态方法便于契约测试）。
     *
     * @param secret 待校验的密钥
     * @throws IllegalStateException 密钥为空 / 等于默认值 / 长度不足时抛出
     */
    public static void validate(String secret) {
        if (!StringUtils.hasText(secret) || WEAK_DEFAULT.equals(secret)) {
            throw new IllegalStateException(
                    "生产环境禁止使用默认 JWT 签名密钥，必须通过环境变量 AIRPOWER_API_ACCESS_TOKEN_SECRET 注入强随机值");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT 签名密钥过短（" + secret.length() + " 字符 < " + MIN_SECRET_LENGTH + "），"
                            + "HS256 建议 ≥ 256 位熵（如 `openssl rand -hex 32` 生成）");
        }
    }
}
