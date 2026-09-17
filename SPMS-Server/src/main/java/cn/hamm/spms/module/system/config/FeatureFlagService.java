package cn.hamm.spms.module.system.config;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * <h1>功能开关 / 灰度发布引擎</h1>
 *
 * <p>现有 {@code ConfigFlag} 是「全局布尔开关」（DB 落库、全量生效，无灰度）。本服务在其
 * {@link ConfigService} 之上补一层**灰度决策**：同一开关可按「百分比 / 白名单 / 黑名单」定向
 * 放量，配合「开关名 → 配置串」的规则 DSL，实现「先 10% 租户 → 50% → 100%」的渐进式发布。</p>
 *
 * <h2>规则 DSL（存在 {@code config} 表 {@code flag}=开关名 的 {@code config} 字段里）</h2>
 * <pre>
 *   on / 1 / true       全局开启
 *   off / 0 / false     全局关闭
 *   pct:25              25% 放量（按 scope 哈希取模，同一 scope 结果稳定，sticky）
 *   allow:1,2,3         白名单（仅 scope ∈ {1,2,3} 开启）
 *   deny:4,5            黑名单（scope ∈ {4,5} 关闭，其余开启）
 * </pre>
 * <p>配置缺失或规则无法解析时回落到 {@code defaultValue}（默认关闭），保证「灰度失败不放大爆炸面」。</p>
 *
 * <h2>scope 是什么</h2>
 * <p>放量维度，通常是<b>租户 ID</b>（多租户系统按租户灰度最合理），也可传用户 ID 做用户级灰度。
 * 传 {@code null} 时：全局开关正常生效；百分比规则按空串哈希（仍是稳定桶，但所有空 scope 同一桶），
 * 白名单/黑名单规则对空 scope 一律按「不在名单」处理。</p>
 *
 * <h2>接入示例</h2>
 * <pre>
 *   if (featureFlagService.isEnabled("feature.new-report", tenantId)) {
 *       // 新报表逻辑
 *   } else {
 *       // 旧报表逻辑
 *   }
 * </pre>
 *
 * <p>存储层目前走 DB 的 {@code config} 表（复用现有 {@link ConfigService}，零新依赖、零迁移）。
 * 后续若要把开关源切到 Nacos（docker-compose 已含 nacos 服务，但后端尚未接 Spring Cloud Alibaba），
 * 只需把本服务「读配置」这步替换为 Nacos 数据源，决策 DSL 与调用方不变（见
 * {@code docs/功能开关与灰度发布方案.md} §3）。</p>
 *
 * <p><b>2026-09-18 分层整改（L7）</b>：本类从 {@code common/feature} 搬到
 * {@code module/system/config} —— 它的存储面就是 {@code config} 表（复用 {@link ConfigService}），
 * 归到配置域比留在"通用横切包"更清楚。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class FeatureFlagService {

    @Autowired
    private ConfigService configService;

    /**
     * 判断开关是否对指定 scope 生效（配置缺失时默认关闭）。
     *
     * @param flag  开关名（对应 config 表的 flag）
     * @param scope 放量维度（租户 ID / 用户 ID，可空）
     * @return 是否生效
     */
    public boolean isEnabled(@NotNull String flag, @Nullable String scope) {
        return isEnabled(flag, scope, false);
    }

    /**
     * 判断开关是否对指定 scope 生效，配置缺失时回落 {@code defaultValue}。
     *
     * @param flag         开关名
     * @param scope        放量维度（可空）
     * @param defaultValue 配置缺失时的默认值
     * @return 是否生效
     */
    public boolean isEnabled(@NotNull String flag, @Nullable String scope, boolean defaultValue) {
        ConfigEntity config = configService.getOrNull(flag);
        if (config == null) {
            return defaultValue;
        }
        boolean decided = decide(config.getConfig(), scope, defaultValue);
        log.debug("feature flag [{}] scope=[{}] rule=[{}] -> {}", flag, scope, config.getConfig(), decided);
        return decided;
    }

    /**
     * 纯决策函数（抽出为静态便于契约测试）：根据规则串与 scope 算出是否生效。
     *
     * @param rule         规则串（DSL，见类注释）
     * @param scope        放量维度（可空）
     * @param defaultValue 规则缺失/无法解析时的默认值
     * @return 是否生效
     */
    public static boolean decide(@Nullable String rule, @Nullable String scope, boolean defaultValue) {
        if (rule == null || rule.trim().isEmpty()) {
            return defaultValue;
        }
        String r = rule.trim().toLowerCase(Locale.ROOT);

        // 全局开关
        if ("on".equals(r) || "1".equals(r) || "true".equals(r)) {
            return true;
        }
        if ("off".equals(r) || "0".equals(r) || "false".equals(r)) {
            return false;
        }

        // 百分比放量：pct:N（N ∈ [0,100]），scope 哈希取模，同一 scope 稳定
        if (r.startsWith("pct:")) {
            int pct = parseIntSafely(r.substring(4), -1);
            if (pct < 0 || pct > 100) {
                return defaultValue;
            }
            return bucket(scope) < pct;
        }

        // 白名单：allow:a,b,c
        if (r.startsWith("allow:")) {
            return containsToken(r.substring(6), scope);
        }

        // 黑名单：deny:a,b,c
        if (r.startsWith("deny:")) {
            return !containsToken(r.substring(5), scope);
        }

        return defaultValue;
    }

    /**
     * 把 scope 稳定哈希到 [0,100) 的桶（FNV-1a 64 位 → mod 100）。
     * 确定性：同一 scope 永远落同一桶，保证灰度期间用户体验不跳变（sticky）。
     */
    static int bucket(@Nullable String scope) {
        String key = scope == null ? "" : scope;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return (int) Math.floorMod(hash, 100L);
    }

    /** 判断 scope 是否命中逗号分隔的白/黑名单（空 scope 一律视为不在名单）。 */
    private static boolean containsToken(String list, @Nullable String scope) {
        if (scope == null || scope.isEmpty()) {
            return false;
        }
        for (String token : list.split(",")) {
            if (scope.equals(token.trim())) {
                return true;
            }
        }
        return false;
    }

    /** 安全解析整数，失败返回 fallback。 */
    private static int parseIntSafely(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
