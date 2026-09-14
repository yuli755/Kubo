package cn.hamm.spms.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * <h1>JWT 签名密钥强度守卫</h1>
 *
 * <p>密钥卫生是「密钥轮换」的前置条件：轮换的前提是有一个可轮换的、真正随机的密钥。
 * airpower 的 {@code access-token-secret} 默认值是弱密钥 {@code 1234567890}（见
 * {@code application.yml}），一旦部署时忘了覆盖环境变量，等于把 JWT 签名密钥写死在
 * 源码里——任何人都能伪造任意身份令牌。</p>
 *
 * <p>历史版本只在 {@code production} profile 生效，但部署链路上没有任何地方强制设置
 * 该 profile（Dockerfile/compose 均未注入 SPRING_PROFILES_ACTIVE）：忘设 profile 的
 * 生产实例会以默认弱密钥静默启动，守卫形同虚设。现在守卫<b>无条件</b>启动校验，
 * 本地开发/测试通过显式开关 {@code kubo.security.allow-weak-default-secret=true}
 * 放行（application.yml 默认打开并醒目标注仅限本地；production profile 显式关闭）。</p>
 *
 * <p>校验规则：</p>
 * <ul>
 *   <li>密钥为空 / 等于默认值 {@value #WEAK_DEFAULT} → 直接抛异常拒绝启动；</li>
 *   <li>密钥长度不足 {@value #MIN_SECRET_LENGTH} 位 → 抛异常拒绝启动（HS256 建议 ≥ 256 位熵）。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class AccessTokenSecretGuard implements ApplicationRunner {

    /** airpower 的弱默认密钥（写死在 application.yml 的 fallback） */
    private static final String WEAK_DEFAULT = "1234567890";
    /** HS256 建议最小密钥长度（字符，≥ 256 位熵） */
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${airpower.api.access-token-secret:}")
    private String accessTokenSecret;

    /**
     * 本地开发显式放行弱默认密钥的开关。任何真实部署不得设置为 true：
     * 置 false（默认值）时无条件 fail-fast，不再依赖 SPRING_PROFILES_ACTIVE 是否被正确注入。
     */
    @Value("${kubo.security.allow-weak-default-secret:false}")
    private boolean allowWeakDefaultSecret;

    @Override
    public void run(ApplicationArguments args) {
        if (allowWeakDefaultSecret) {
            log.warn("JWT 密钥守卫已放行弱默认密钥（kubo.security.allow-weak-default-secret=true），"
                    + "此开关仅限本地开发，任何部署环境必须移除该配置！");
            return;
        }
        validate(accessTokenSecret);
        log.info("JWT 签名密钥已就绪（长度 {}），未使用默认弱密钥", accessTokenSecret.length());
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
                    "禁止使用默认 JWT 签名密钥，必须通过环境变量 AIRPOWER_API_ACCESS_TOKEN_SECRET 注入强随机值"
                            + "（本地开发可显式设置 kubo.security.allow-weak-default-secret=true 放行）");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT 签名密钥过短（" + secret.length() + " 字符 < " + MIN_SECRET_LENGTH + "），"
                            + "HS256 建议 ≥ 256 位熵（如 `openssl rand -hex 32` 生成）");
        }
    }
}
