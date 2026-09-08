package cn.hamm.spms.module.system.datapermission.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <h1>数据权限运行模式配置</h1>
 *
 * <p>对应《供应链权限治理二次改造审计与实施计划》阶段 7「观察模式 → 强制模式」的灰度要求。
 * 之前代码是「编译期硬编码 fail-open」，既不能灰度也不能回滚；本配置把三态开关做实。</p>
 *
 * <p>新增开关的规矩（本模块欠过一次教训）：<b>凡是放宽可见范围的开关，默认必须 false</b>，
 * 且必须同时写进 {@code application.yml}（没人看见的开关等于没有开关）。目前有三个：
 * {@code row-write.enabled}、{@code hierarchy.department-enabled}、{@code hierarchy.factory-enabled}。</p>
 *
 * <pre>{@code
 * kubo:
 *   data-permission:
 *     mode: shadow                 # off | shadow | enforce
 *     enforce-resources:           # 逐资源灰度名单（见下方 enforceResources 说明）
 *       - wms.inventory
 *       - mes.order
 *     deny-unregistered: false     # enforce 下未注册资源是否默认拒绝（阶段 7 末尾改 true）
 *     factory-dimension-enabled: false  # 工厂维度是否已落地（三条前置条件见 DimensionGuard）
 *     tenant-required: false       # 用户无租户归属时是否直接拒绝（回填完成后改 true）
 *     default-tenant-id: 1         # tenant-required=false 时的兜底租户（平台默认租户）
 *     shadow-diff-log: true        # 观察模式是否输出「本应被过滤」的明细日志
 *     row-write:
 *       enabled: false             # 写侧行级保护（改/删既存行必须在自己的数据范围内）
     *     hierarchy:
     *       department-enabled: false  # DEPARTMENT 策略是否展开到子部门（放宽可见范围，默认关）
     *       factory-enabled: false     # FACTORY 策略是否展开到整棵子树（放宽可见范围，默认关）
     *       ttl-seconds: 300           # 部门/工厂树快照有效期
     *       max-depth: 32              # 展开层数上限（脏数据成环时的兜底）
     *     shadow:
     *       sample-rate: 0.02          # 影子双跑采样率
     *       persist: true              # 差异是否落 data_permission_diff_log
     *       retention-cleanup-enabled: false # 是否自动清理到期证据（默认关闭，归档后显式开启）
     *       retention-days: 30          # 两张证据表至少保留天数
     *       retention-cleanup-cron: 0 15 3 * * * # 每日清理时间
     * }</pre>
 *
 * @author Hamm.cn
 */
@Data
@Component
@ConfigurationProperties(prefix = "kubo.data-permission")
public class DataPermissionProperties {

    /**
     * 运行模式
     */
    public enum Mode {
        /** 完全不参与（行为等价于改造前，用于回滚） */
        OFF,
        /** 只计算与记录，不改变查询结果（灰度观察） */
        SHADOW,
        /** 真正注入过滤条件与拒绝写入 */
        ENFORCE
    }

    /**
     * 全局模式，默认 {@link Mode#SHADOW}：
     * 上线这一步不改变任何业务结果，但能立刻暴露"开了 enforce 会发生什么"。
     */
    private Mode mode = Mode.SHADOW;

    /**
     * 资源白名单，用于逐资源灰度：
     * <ul>
     *   <li>{@code mode=enforce} —— 名单为空表示全量生效；非空则只有名单内资源 enforce，其余降为 shadow；</li>
     *   <li>{@code mode=shadow} —— 名单内资源<b>提前</b>进入 enforce（观察够了就先放开一个）。</li>
     * </ul>
     */
    private Set<String> enforceResources = new LinkedHashSet<>();

    /**
     * enforce 模式下，未注册 / 未启用的资源是否默认拒绝（fail-closed）。
     */
    private boolean denyUnregistered = false;

    /**
     * 用户没有任何租户归属时是否拒绝请求。
     * 迁移回填完成前保持 false（回落 {@link #defaultTenantId}），回填后置 true。
     */
    private boolean tenantRequired = false;

    /**
     * 平台默认租户 ID（与 {@code tenant} 表的 code=default 记录一致）。
     */
    private Long defaultTenantId = 1L;

    /**
     * 工厂（FACTORY）维度是否已落地（默认 false = 策略保存时直接拒绝该维度）。
     *
     * <p>打开前必须核对三条前置条件（建 {@code user_factory} 表并回填、{@code PermissionContext
     * .factoryIds()} 真实解析、行权限与写入侧共用同一份 factoryIds），
     * 详见 {@code DimensionGuard#isFactoryAvailable()}。缺一条就别开：这个维度在数据模型上
     * 是恒空的，开了要么假安全（策略永不命中），要么自锁（条件树渲染成恒假、全员空结果）。</p>
     */
    private boolean factoryDimensionEnabled = false;

    /**
     * 观察模式是否输出差异明细日志。
     */
    private boolean shadowDiffLog = true;

    /**
     * 是否审计「明文敏感字段访问」。
     */
    private boolean auditPlaintextAccess = true;

    /**
     * 影子双跑（shadow 模式下用 COUNT 比对"加条件/不加条件"的行数差）
     */
    private Shadow shadow = new Shadow();

    /**
     * 写侧行级保护（见 {@code RowWriteGuard}）
     */
    private RowWrite rowWrite = new RowWrite();

    /**
     * 层级（部门树）配置
     */
    private Hierarchy hierarchy = new Hierarchy();

    /**
     * 权限变更 Outbox 派发配置
     */
    private Outbox outbox = new Outbox();

    /**
     * 权限变更 Outbox 派发配置
     */
    @Data
    public static class Outbox {

        /**
         * 是否启用 Outbox Dispatcher。默认 <b>false</b>：Dispatcher 依赖真实 Redis 与调度线程，
         * 本地 / 测试环境不启动（避免集成测试启动即拉取），真实环境回填并核对 {@code permission_change_outbox}
         * 表后再开。写侧落库（{@code PermissionChangeOutboxRecorder}）与 Dispatcher 解耦：关着 Dispatcher
         * 时事件照样持久化，只是不会自动重投。
         */
        private boolean enabled = false;

        /**
         * Dispatcher 轮询间隔（毫秒）
         */
        private long pollMs = 5_000L;
    }

    /**
     * 部门树闭包展开配置
     */
    @Data
    public static class Hierarchy {

        /**
         * 是否把 {@code DEPARTMENT} 策略的主体展开到子部门。
         *
         * <p>默认 <b>false</b>，因为它是一次<b>放宽</b>：开着时配在父部门的策略会连带覆盖所有子部门的
         * 数据，而现网已经存在的 DEPARTMENT 策略大概都是按"只影响直属部门"配的。
         * 灰度顺序与写侧守卫相同：先在影子环境比对"多放行多少行"，再逐资源进 enforce 名单。</p>
         */
        private boolean departmentEnabled = false;

        /**
         * 是否把 {@code FACTORY} 策略的主体（工厂 = 仓库/工厂树顶层节点）展开到整棵子树。
         *
         * <p>默认 <b>false</b>：与 {@link #departmentEnabled} 同一条理由 —— 展开是<b>放宽</b>
         * 可见范围（配在工厂上的策略会连带覆盖其下所有仓库），现网既有的 FACTORY 策略（若有）
         * 大概率按「只影响工厂节点本身」理解，放开前必须先影子比对「多放行多少行」。
         * 注意这是「层级展开」开关，与 {@code kubo.data-permission.factory-dimension-enabled}
         * （维度是否落地）是两回事：本开关只管「已解析出的工厂集合要不要向下闭包」。</p>
         */
        private boolean factoryEnabled = false;

        /**
         * 部门树快照有效期（秒）。
         *
         * <p>只在<b>本进程</b>缓存：权限范围的缓存放 Redis 的话，"改了部门树但某个节点还没过期"
         * 直接等于可见范围不一致，而 30 分钟的默认 TTL 对权限来说太长。部门改动会显式失效本地快照。</p>
         */
        private long ttlSeconds = 300L;

        /**
         * 展开层数上限：超出即停止并标记 truncated（宁可少给权限，也不让脏数据把请求打挂）
         */
        private int maxDepth = 32;
    }

    /**
     * 写侧行级保护配置
     */
    @Data
    public static class RowWrite {

        /**
         * 是否启用。默认 <b>false</b>，且这个默认值是有原因的：
         * 读侧条件绑不上是"查不到数据"，写侧条件绑不上是"<b>保存被拒</b>"——
         * 后者直接打断业务。而盘点显示不少资源的维度列目前根本绑不上（§19.25/§19.26），
         * 一开就是全员改不了单。灰度顺序固定：维度面板清零 → 开本开关看 shadow 指标 → 再进 enforce 名单。
         */
        private boolean enabled = false;
    }

    /**
     * 影子双跑配置
     */
    @Data
    public static class Shadow {

        /**
         * 采样率 0..1。双跑要多打一次 COUNT，默认只抽 2%：
         * 够统计差异率，又不至于把库压垮。
         */
        private double sampleRate = 0.02D;

        /**
         * 是否把差异写入 {@code data_permission_diff_log}（关掉就只打指标）
         */
        private boolean persist = true;

        /**
         * 单次比对允许记录的最大明细行数（超过则只记行数不记主键）
         */
        private int maxDetailRows = 200;

        /**
         * 是否自动清理到期的影子与 ORM 对账证据。
         *
         * <p>清理不是"把审计关掉"：任务只删除超过 {@link #retentionDays} 的记录，
         * 并且默认保留至少 30 天。需要更长留存时调大天数；需要长期归档时应在数据库备份/归档层
         * 先复制两张证据表，再打开清理，应用本身不把大表搬到业务库里。</p>
         */
        private boolean retentionCleanupEnabled = false;

        /**
         * 影子证据保留天数，必须为正数
         */
        private int retentionDays = 30;

        /**
         * 到期证据清理 cron；由 {@code DataPermissionEvidenceRetention} 消费
         */
        private String retentionCleanupCron = "0 15 3 * * *";
    }

    /**
     * 判断指定资源的生效模式
     *
     * @param resourceKey 资源标识
     * @return OFF / SHADOW / ENFORCE
     */
    public Mode effectiveMode(String resourceKey) {
        if (mode == Mode.OFF) {
            return Mode.OFF;
        }
        if (mode == Mode.ENFORCE) {
            // 全量 enforce 时，白名单是"例外清单"：名单外（非空时）先降回 shadow
            return enforceResources.isEmpty() || enforceResources.contains(resourceKey)
                    ? Mode.ENFORCE : Mode.SHADOW;
        }
        // 全局 shadow 时，白名单是"提前放开清单"：名单内资源先 enforce
        return enforceResources.contains(resourceKey) ? Mode.ENFORCE : Mode.SHADOW;
    }

    /**
     * 该资源是否会真正改变查询/写入结果
     *
     * @param resourceKey 资源标识
     * @return 是否强制
     */
    public boolean enforced(String resourceKey) {
        return effectiveMode(resourceKey) == Mode.ENFORCE;
    }

    /**
     * 该资源是否需要计算权限条件（OFF 时全部短路）
     *
     * @param resourceKey 资源标识
     * @return 是否参与计算
     */
    public boolean computes(String resourceKey) {
        return effectiveMode(resourceKey) != Mode.OFF;
    }
}
