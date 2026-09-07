package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionOrmParityRepository;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleOp;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <h1>影子双跑比对</h1>
 *
 * <p>回答两个只有真实数据才能回答的问题：<b>这条权限条件一旦 enforce，会少返回多少行？</b>
 * 以及（§19.36 新加的第三个 {@code COUNT}）<b>同一棵条件树交给 MyBatis 去数，数出来的是不是同一批行？</b>
 * 前一个问两次不带条件/带条件的差值，记进 {@code data_permission_diff_log}；后一个记进
 * {@code data_permission_orm_parity_log}，只有当该资源登记了 {@link RowParityCounter} 才跑
 * （{@code kubo.data-permission.mapper-read.enabled} 关着时一个探针 Bean 都没有，因此一条对账 SQL 也不会发）。
 * 之所以能只看 {@code COUNT}，是因为行级过滤的失败模式只有"多滤/少滤"，与排序、分页、投影无关。</p>
 *
 * <p>三条纪律：</p>
 * <ol>
 *   <li><b>采样</b>：默认 2%（{@code kubo.data-permission.shadow.sample-rate}），
 *       双跑是额外一次 COUNT，不采样就是在拿生产 QPS 换观测；</li>
 *   <li><b>异步 + 有界队列</b>：比对永不占用业务线程；队列满时<b>丢任务并打点</b>，
 *       不阻塞、不抛给业务；</li>
 *   <li><b>只在 SHADOW 生效</b>：OFF 不参与，ENFORCE 下条件已经真注入，再双跑纯属浪费。</li>
 * </ol>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class ShadowDiffService {

    /**
     * 比对结论分类
     */
    public static String verdictOf(Long baselineCount, Long guardedCount) {
        if (baselineCount == null || guardedCount == null) {
            return DataPermissionDiffEntity.VERDICT_ERROR;
        }
        if (guardedCount.equals(baselineCount)) {
            return DataPermissionDiffEntity.VERDICT_CONSISTENT;
        }
        // 加了条件反而更多 = 条件语义与预期不符（几乎一定是把 OR 写成了 AND 之类的实现 bug）
        return guardedCount < baselineCount
                ? DataPermissionDiffEntity.VERDICT_EXPECTED_FILTER
                : DataPermissionDiffEntity.VERDICT_ANOMALY;
    }

    /**
     * Mapper 侧对账的输入：一棵已经建好的树 + 该资源的物理表名
     *
     * <p>{@code rules} 由 {@code DataPermissionSpecification} 在<em>建树</em>那一步顺手交出来，
     * 本类不再自己按策略重算一遍 —— 那是 §19.35 刚清掉的那种"第二份判据"。表名同理：探针数的是
     * 资源登记的那张表，不是从实体名推出来的（{@code MaterialMapperReadParityTest} 钉过这件事）。</p>
     *
     * <p>{@code context} 是 {@link PermissionContext} 的<em>值对象</em>，不是 ThreadLocal：
     * 与 userId/tenantId 一样在业务线程上取好再带进异步任务，后台线程去读 ThreadLocal
     * 会读到"上一个占用该线程的请求"的身份。</p>
     *
     * @param tableAlias 片段里的列限定名（资源的物理表名）
     * @param rules      一棵棵策略条件树（与 JPA 侧注入的是同一批对象）
     * @param context    主体上下文值对象
     */
    public record OrmParityProbe(String tableAlias, List<PermissionRule> rules, PermissionContext context) {

        public OrmParityProbe {
            rules = rules == null || rules.isEmpty() ? List.of() : List.copyOf(rules);
        }

        /**
         * 能不能拿去对账：表名缺了就<em>不猜</em>（猜成类名驼峰转下划线正是 §19.34 写进基类注释里的坑）
         *
         * @return 可以对账返回 true
         */
        public boolean usable() {
            return !rules.isEmpty() && tableAlias != null && !tableAlias.isBlank() && context != null;
        }
    }

    /**
     * 对账结论分类（方向性判据，与 {@link #verdictOf} 不同：这里比的不是"少不少行"，是"两边同不同"）
     *
     * <p>两个方向不等价，所以不合成一个"有差异"：</p>
     * <ul>
     *   <li>{@code mapper > jpa} = Mapper 多放行 = 朝泄露那一侧偏，是阻塞放开的唯一一种差异。
     *       最常见成因恰是"登记了归属列而实体没映射它"：SQL 侧没有元模型可问、登记的列全用，
     *       于是多一个 OR 分支、结果集变大（§19.35 把这个形状说成了"更严"，方向反了，纠正见该节）。</li>
     *   <li>{@code mapper < jpa} = Mapper 更保守，泄露不了，但有人会少看数据 ⇒ 不阻塞、留痕即可。</li>
     * </ul>
     *
     * @param jpaCount   JPA 侧加条件后的行数
     * @param mapperCount Mapper 侧加同一段片段后的行数
     * @return 结论常量（见 {@code DataPermissionOrmParityEntity.VERDICT_*}）
     */
    public static String parityVerdictOf(Long jpaCount, Long mapperCount) {
        if (jpaCount == null || mapperCount == null) {
            return DataPermissionOrmParityEntity.VERDICT_ERROR;
        }
        if (mapperCount.equals(jpaCount)) {
            return DataPermissionOrmParityEntity.VERDICT_CONSISTENT;
        }
        return mapperCount < jpaCount
                ? DataPermissionOrmParityEntity.VERDICT_MAPPER_STRICTER
                : DataPermissionOrmParityEntity.VERDICT_MAPPER_LOOSER;
    }

    private final DataPermissionProperties properties;
    private final DataPermissionAudit audit;
    private final ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider;
    private final ObjectProvider<RowParityRegistry> parityRegistryProvider;
    private final ObjectProvider<DataPermissionOrmParityRepository> parityRepositoryProvider;
    private final SqlPermissionRuleRenderer sqlRenderer = new SqlPermissionRuleRenderer();

    @PersistenceContext
    private EntityManager entityManager;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            runnable -> {
                Thread thread = new Thread(runnable, "dp-shadow-diff");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public ShadowDiffService(DataPermissionProperties properties,
                             DataPermissionAudit audit,
                             ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider,
                             ObjectProvider<RowParityRegistry> parityRegistryProvider,
                             ObjectProvider<DataPermissionOrmParityRepository> parityRepositoryProvider) {
        this.properties = properties;
        this.audit = audit;
        this.diffRepositoryProvider = diffRepositoryProvider;
        this.parityRegistryProvider = parityRegistryProvider;
        this.parityRepositoryProvider = parityRepositoryProvider;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /**
     * 是否应当采样（先判模式与采样率，避免为被丢弃的调用付 COUNT 成本）
     */
    public boolean sampled(String resourceKey) {
        if (properties.effectiveMode(resourceKey) != DataPermissionProperties.Mode.SHADOW) {
            return false;
        }
        double rate = properties.getShadow() == null ? 0D : properties.getShadow().getSampleRate();
        return rate > 0D && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < rate;
    }

    /**
     * 对一次查询做影子比对（不改变业务结果；异常只记账）
     *
     * @param resourceKey   资源标识
     * @param action        动作
     * @param entityClass   被过滤的实体类
     * @param guardedFilter enforce 后<b>本应</b>注入的条件；null 表示无条件
     * @param context       主体上下文（只用于记录是谁的查询）
     * @param probe         Mapper 侧对账的输入；{@code null} = 该资源本轮不参与对账（不补猜）
     */
    public void compare(String resourceKey,
                        DataPermissionAction action,
                        Class<?> entityClass,
                        Specification<?> guardedFilter,
                        PermissionContext context,
                        OrmParityProbe probe) {
        if (guardedFilter == null || !sampled(resourceKey)) {
            return;
        }
        Long userId = context == null ? null : context.userId();
        Long tenantId = context == null ? null : context.tenantId();
        // 身份与条件都在<em>业务线程</em>上取好再带进异步任务：PermissionContext 挂在 ThreadLocal 上，
        // 后台线程去读只会读到"上一个占用该线程的请求"的身份，或者什么都没有。
        // 所以这里传的是值（userId/tenantId）与已经绑定 context 的 Specification，而不是 context 本身。
        try {
            executor.execute(() -> doCompare(resourceKey, action, entityClass, guardedFilter, userId, tenantId, probe));
        }
        catch (RejectedExecutionException ex) {
            // 队列满：丢掉这次比对，但必须让人看见"观测有缺口"
            audit.shadow(resourceKey, "diff-probe-saturated", "影子比对队列已满，本次未比对");
        }
    }

    /**
     * 包级可见而不是私有：本类的真实行为要连库跑才看得出来（H2 对账测同步调它）。
     * 留个私有方法再配一个"仅供测试"的转发，读代码的人会先怀疑那是条后门，不如把形状写明白。
     */
    void doCompare(String resourceKey,
                   DataPermissionAction action,
                   Class<?> entityClass,
                   Specification<?> guardedFilter,
                   Long userId,
                   Long tenantId,
                   OrmParityProbe probe) {
        long started = System.currentTimeMillis();
        Long baseline = null;
        Long guarded = null;
        String detail = null;
        try {
            baseline = count(entityClass, null);
            guarded = count(entityClass, guardedFilter);
        }
        catch (RuntimeException ex) {
            // 比对失败多数是"列不存在"，与 binding 指标同源，正好互相印证
            detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            audit.binding(resourceKey, "shadow-compare-failed", detail);
            log.debug("[data-permission][shadow] 比对失败 resource={}: {}", resourceKey, ex.getMessage());
        }
        long cost = System.currentTimeMillis() - started;
        String verdict = detail != null ? DataPermissionDiffEntity.VERDICT_ERROR : verdictOf(baseline, guarded);

        if (properties.getShadow().isPersist()) {
            persistDiff(resourceKey, action, verdict, baseline, guarded, cost, detail, userId, tenantId);
        }
        else {
            audit.shadow(resourceKey, "diff:" + verdict, "baseline=" + baseline + " guarded=" + guarded + " cost=" + cost + "ms");
        }
        // 这里以前是一个 return（落库关着就不往下走）。留着它，"我只想打点、先不落库"这个配置就会
        // 顺手把第三个 COUNT 也关掉 —— 一个开关值管两件事，下次就有人为了拿到对账证据去开 persist，
        // 然后被自己的埋点表淹掉。对账因此排在两条分支之后，各自判自己的开关。
        runParity(resourceKey, action, probe, guarded, userId, tenantId);
    }

    /**
     * 把"加条件会不会少行"这条差异写进 {@code data_permission_diff_log}
     *
     * <p>调用方已经判过 {@code shadow.persist}：这里是"要落库"的那条分支，只留"表不在就不写"。
     * 缺 Bean 与写失败都只 debug —— 观测链路不许把异步任务炸掉。</p>
     */
    private void persistDiff(String resourceKey,
                             DataPermissionAction action,
                             String verdict,
                             Long baseline,
                             Long guarded,
                             long cost,
                             String detail,
                             Long userId,
                             Long tenantId) {
        DataPermissionDiffRepository repository = diffRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return;
        }
        try {
            DataPermissionDiffEntity entity = new DataPermissionDiffEntity()
                    .setResourceKey(resourceKey)
                    .setActionName(action == null ? "QUERY" : action.name())
                    .setBaselineCount(baseline)
                    .setGuardedCount(guarded)
                    .setVerdict(verdict)
                    .setCostMs(cost)
                    .setDetail(detail);
            entity.setUserId(userId);
            entity.setTenantId(tenantId);
            // 影子比对记录由后台线程写入，无 HTTP 上下文：本实体不含 @SensitiveField 字段，
            // 因此不需要 BaseService 的敏感字段兜底（脱敏登记也天然是空的）
            repository.save(entity);
        }
        catch (RuntimeException ex) {
            log.debug("[data-permission][shadow] 差异落库失败：{}", ex.getMessage());
        }
    }

    /**
     * 第三个 COUNT：同一棵条件树交给 MyBatis 数一遍，与 JPA 侧的行数对账
     *
     * <p>三条纪律：</p>
     * <ol>
     *   <li><b>没有探针就不跑，也不假装跑过</b>：探针来自 {@link RowParityRegistry}，
     *       而注册表只在 {@code mapper-read.enabled=true} 时才存在（见 {@code MapperReadConfiguration}）；
     *       未登记的资源留一条 {@code orm-parity:no-probe} 指标，让"为什么没有对账数据"有地方查。</li>
     *   <li><b>组合方向跟着生产走</b>：多策略之间用 {@code ALL_OF}，与 25 个服务调用点
     *       {@code predicateList.addAll(...)} 后 {@code builder.and(...)} 的语义一致，写错成 ANY_OF
     *       会得到"两边都放行更多行、对账却一致"的假绿灯。</li>
     *   <li><b>任何异常都只影响证据、不影响业务</b>：渲染抛的（列名非法、关联路径不支持）、
     *       换写抛的（片段与参数不吻合）都归到 {@code ERROR} 一行，业务线程早就返回了。</li>
     * </ol>
     */
    private void runParity(String resourceKey,
                           DataPermissionAction action,
                           OrmParityProbe probe,
                           Long jpaCount,
                           Long userId,
                           Long tenantId) {
        if (probe == null) {
            return;
        }
        if (!probe.usable()) {
            // 表名没登记、或上下文没带全：这是配置缺口，必须看得见。
            // 静默 return 的话，"这个资源怎么一直没有对账数据"就只能靠读代码回答
            audit.shadow(resourceKey, "orm-parity:skipped",
                    "探针在但对账输入不齐（rules=" + probe.rules().size() + ", tableAlias=" + probe.tableAlias() + "）");
            return;
        }
        RowParityRegistry registry = parityRegistryProvider == null ? null : parityRegistryProvider.getIfAvailable();
        RowParityCounter counter = registry == null ? null : registry.counterFor(resourceKey);
        if (counter == null) {
            audit.shadow(resourceKey, "orm-parity:no-probe", "该资源没有登记 Mapper 行数探针，本轮不对账");
            return;
        }
        long started = System.currentTimeMillis();
        Long mapperCount = null;
        String fragmentSql = null;
        String detail = null;
        try {
            RowPermissionPredicate fragment = sqlRenderer.render(
                    PermissionRule.group(PermissionRuleOp.ALL_OF, probe.rules()),
                    probe.tableAlias(),
                    new PermissionRuleContext(probe.context()));
            // fragment == null 是"这一树不贡献条件"，与"不加条件"同义：空片段照样数一次，
            // 让"两边都不加条件"也留下一条 CONSISTENT 证据，而不是无声跳过
            fragmentSql = fragment == null ? "" : fragment.sql();
            // 显式类型参数：三元两臂一个是 Map.of、一个是 Map<String,Object>，
            // 让目标类型去推虽然能过 javac，但读的人要自己确认一遍 —— 写出来省这笔账
            mapperCount = counter.countByPermissionFragment(fragment == null
                    ? Map.<String, Object>of(RowPermissionPredicate.FRAGMENT_KEY, "")
                    : fragment.myBatisStatementParameters());
        }
        catch (RuntimeException ex) {
            detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            audit.binding(resourceKey, "orm-parity-failed", detail);
        }
        long cost = System.currentTimeMillis() - started;
        String verdict = detail != null ? DataPermissionOrmParityEntity.VERDICT_ERROR
                : parityVerdictOf(jpaCount, mapperCount);
        persistParity(resourceKey, action, probe, verdict, jpaCount, mapperCount, fragmentSql, cost, detail,
                userId, tenantId);
    }

    /**
     * 落对账证据；{@code shadow.persist} 关掉时只打点（与差异那条同一个开关，同一个含义：要不要写库）
     */
    private void persistParity(String resourceKey,
                               DataPermissionAction action,
                               OrmParityProbe probe,
                               String verdict,
                               Long jpaCount,
                               Long mapperCount,
                               String fragmentSql,
                               long cost,
                               String detail,
                               Long userId,
                               Long tenantId) {
        audit.shadow(resourceKey, "orm-parity:" + verdict,
                "jpa=" + jpaCount + " mapper=" + mapperCount + " cost=" + cost + "ms");
        if (!properties.getShadow().isPersist()) {
            return;
        }
        DataPermissionOrmParityRepository repository = parityRepositoryProvider == null
                ? null : parityRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return;
        }
        try {
            repository.save(new DataPermissionOrmParityEntity()
                    .setResourceKey(resourceKey)
                    .setActionName(action == null ? "QUERY" : action.name())
                    .setTableName(probe.tableAlias())
                    .setJpaCount(jpaCount)
                    .setMapperCount(mapperCount)
                    .setVerdict(verdict)
                    .setFragmentSql(truncate(fragmentSql, 512))
                    .setCostMs(cost)
                    .setDetail(detail));
        }
        catch (RuntimeException ex) {
            // 证据写不进去不能把异步任务炸掉：这条路径整个都在后台线程上，抛出去就是一条没人认领的错误日志
            log.debug("[data-permission][shadow] 对账落库失败：{}", ex.getMessage());
        }
    }

    /**
     * 片段原文按列宽截断：留一半也够定位是哪几个列，比整列存不进去、异常把证据带走要好
     */
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * 单表 COUNT（不带任何关联，避免把 JOIN 放大成行差噪声）
     */
    private Long count(Class<?> entityClass, Specification<?> filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<?> root = query.from(entityClass);
        query.select(builder.count(root));
        if (filter != null) {
            // 用裸类型调用：Specification<?> 的捕获类型与 Root<?> 的捕获类型在 javac 下
            // 无法互相证明同源，泛型写法会编译失败；这里语义上就是同一实体，裸类型 + 抑制最省事。
            Predicate predicate = toPredicateUnsafe(filter, root, query, builder);
            if (predicate != null) {
                query.where(predicate);
            }
        }
        Long result = entityManager.createQuery(query).getSingleResult();
        return result == null ? 0L : result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate toPredicateUnsafe(Specification filter, Root root, CriteriaQuery query, CriteriaBuilder builder) {
        return filter.toPredicate(root, query, builder);
    }

    /**
     * 便于单测注入
     */
    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
