package cn.hamm.spms.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * <h1>生产环境数据库凭据守卫</h1>
 *
 * <p>与 {@link AccessTokenSecretGuard} 同一条纪律：弱默认值一旦在生产被静默接受，等于把凭据写死在源码里。</p>
 *
 * <p>{@code application-production.yml} 的数据库密码仍是「环境变量注入 + 弱默认兜底」：</p>
 * <pre>{@code
 *   username: '${DB_USERNAME:kubo}'
 *   password: '${DB_PASSWORD:kubo}'
 * }</pre>
 *
 * <p>JWT 签名密钥有 fail-fast 守卫，数据库密码却没有——忘记设 {@code DB_PASSWORD} 时，生产会静默用
 * 弱默认 {@code kubo/kubo} 连库（源码里明文可查的凭据）。本守卫在生产 profile 启动时 fail-fast：</p>
 * <ul>
 *   <li>密码为空 → 拒绝启动（数据库连接不可能无密码，属配置缺失）；</li>
 *   <li>密码等于弱默认 {@value #WEAK_DEFAULT} → 拒绝启动（源码明文凭据）。</li>
 * </ul>
 *
 * <p>刻意<strong>不</strong>加「密码长度 / 复杂度」校验：数据库口令的强度策略由 DBA 决定，本守卫只拦
 * 「源码里明写的那个默认值」这一种确定无疑的误配，不越权替运维制定口令策略。</p>
 *
 * <p>仅在 {@code production} profile 生效；{@code local}/{@code test} 用 {@code root}/{@code kubo}
 * 连本地库无妨，不拦。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
@Profile("production")
public class DatabaseCredentialGuard implements ApplicationRunner {

    /** application-production.yml 里写死的弱默认数据库密码 */
    private static final String WEAK_DEFAULT = "kubo";

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Override
    public void run(ApplicationArguments args) {
        validate(datasourcePassword);
        log.info("数据库密码已就绪（非默认弱凭据），生产未使用源码明文凭据");
    }

    /**
     * 校验数据库密码（抽出为静态方法便于契约测试）。
     *
     * @param password 待校验的密码
     * @throws IllegalStateException 密码为空 / 等于弱默认值时抛出
     */
    public static void validate(String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "生产环境禁止使用空数据库密码，必须通过环境变量 DB_PASSWORD 注入真实凭据");
        }
        if (WEAK_DEFAULT.equals(password)) {
            throw new IllegalStateException(
                    "生产环境禁止使用源码弱默认数据库密码 kubo，必须通过环境变量 DB_PASSWORD 注入真实凭据");
        }
    }
}
