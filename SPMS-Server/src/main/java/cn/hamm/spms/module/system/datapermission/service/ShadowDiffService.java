package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <h1>影子双跑比对</h1>
 *
 * <p>回答一个只有真实数据才能回答的问题：<b>这条权限条件一旦 enforce，会少返回多少行？</b>
 * 做法是对同一实体跑两次 {@code COUNT}：不带条件、带条件，把差值记进
 * {@code data_permission_diff_log}。之所以能只看 {@code COUNT}，是因为
 * 行级过滤的失败模式只有"多滤/少滤"，与排序、分页、投影无关。</p>
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

    private final DataPermissionProperties properties;
    private final DataPermissionAudit audit;
    private final ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider;

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
                             ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider) {
        this.properties = properties;
        this.audit = audit;
        this.diffRepositoryProvider = diffRepositoryProvider;
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
     */
    public void compare(String resourceKey,
                        DataPermissionAction action,
                        Class<?> entityClass,
                        Specification<?> guardedFilter,
                        PermissionContext context) {
        if (guardedFilter == null || !sampled(resourceKey)) {
            return;
        }
        Long userId = context == null ? null : context.userId();
        Long tenantId = context == null ? null : context.tenantId();
        try {
            executor.execute(() -> doCompare(resourceKey, action, entityClass, guardedFilter, userId, tenantId));
        }
        catch (RejectedExecutionException ex) {
            // 队列满：丢掉这次比对，但必须让人看见"观测有缺口"
            audit.shadow(resourceKey, "diff-probe-saturated", "影子比对队列已满，本次未比对");
        }
    }

    private void doCompare(String resourceKey,
                           DataPermissionAction action,
                           Class<?> entityClass,
                           Specification<?> guardedFilter,
                           Long userId,
                           Long tenantId) {
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

        if (!properties.getShadow().isPersist()) {
            audit.shadow(resourceKey, "diff:" + verdict, "baseline=" + baseline + " guarded=" + guarded + " cost=" + cost + "ms");
            return;
        }
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
            repository.save(entity);
        }
        catch (RuntimeException ex) {
            log.debug("[data-permission][shadow] 差异落库失败：{}", ex.getMessage());
        }
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
