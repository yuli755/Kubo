package cn.hamm.spms.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>防爆破守卫阈值配置（R45 参数化）</h1>
 *
 * <p>收编 {@link LoginBruteForceGuard} / {@link ResetCodeBruteForceGuard} 原先写死的全部阈值常量
 * （登录守卫 javadoc 预留的「可配置化」在此兑现）。<b>每个字段的默认值就是改造前的常量值</b>——
 * 不配置 = 行为逐位不变，参数化本身不引入任何行为漂移；这是本次改造的验收线。</p>
 *
 * <p>归树说明：挂既有 {@code kubo.security} 树（与 {@code allow-weak-default-secret}、
 * {@code trusted-proxies} 同域），防爆破子树 {@code kubo.security.brute-force}。那两键走
 * {@code @Value} 消费，与本绑定器无冲突。</p>
 *
 * <p>运维暴露面：{@code application-production.yml} 只把「按部署拓扑才需要调」的四键
 * （两守卫的 lock-seconds / ip-max-fail-attempts）写成环境变量占位符；其余键留在代码默认，
 * 配置面宁窄勿宽——每多一个环境变量就多一个部署误操作面。</p>
 *
 * @author Hamm.cn
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kubo.security.brute-force")
public class SecurityGuardProperties {

    /**
     * Redis 运行时故障降级告警的最小间隔（秒），默认 60。
     * <p>redis 抖动期每个请求都会走降级路径，webhook 必须节流，否则告警风暴会淹没
     * 真正需要看这条告警的人；WARN 日志不节流（本地排障要看全貌）。</p>
     */
    private long degradeAlertIntervalSeconds = 60;

    /** 登录守卫阈值 */
    private final Login login = new Login();

    /** 重置验证码守卫阈值 */
    private final Reset reset = new Reset();

    /**
     * 登录守卫（原 LoginBruteForceGuard 常量原值搬入）。
     */
    @Data
    public static class Login {
        /** 账号维度：窗口内允许的最大失败次数 */
        private int maxFailAttempts = 5;
        /** 失败计数窗口（秒） */
        private int failWindowSeconds = 15 * 60;
        /** 锁定时长（秒） */
        private int lockSeconds = 15 * 60;
        /** IP 维度：允许的最大失败次数（高阈值，防 NAT 误伤） */
        private int ipMaxFailAttempts = 50;
    }

    /**
     * 重置验证码守卫（原 ResetCodeBruteForceGuard 常量原值搬入）。
     */
    @Data
    public static class Reset {
        /** 账号维度：窗口内允许的最大验码失败次数 */
        private int maxFailAttempts = 5;
        /**
         * 失败计数窗口（秒）：默认与验证码 TTL（UserService.CACHE_CODE_EXPIRE_SECOND，5 分钟）
         * 同值，让计数窗口覆盖一枚码的完整生命周期。调大它可以，调小到码 TTL 以下会让
         * 「一枚码的寿命内只挡一半尝试次数」——锁仍按 maxFailAttempts 生效，只是窗口提前清零。
         */
        private int failWindowSeconds = 5 * 60;
        /** 锁定时长（秒）：刻意长于计数窗口，换码续猜也要先等锁到期 */
        private int lockSeconds = 15 * 60;
        /** IP 维度：允许的最大失败次数（口径同登录守卫） */
        private int ipMaxFailAttempts = 50;
    }
}
