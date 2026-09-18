package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.base.EntityRepositories;
import cn.hamm.spms.framework.curd.base.ICurdRepository;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <h1>影子比对（J4：单路径取证）</h1>
 *
 * <p>回答一个问题：<b>这条权限条件一旦 enforce，会少返回多少行？</b>
 * 做法是同样一条 COUNT 数两遍 —— 不带权限条件（baseline）与带上 enforce 本会注入的条件（guarded），
 * 两次都走<b>同一条生产路径</b>（实体仓储 + MyBatis-Plus wrapper），差值就是"条件强度"。
 * 结论落进 {@code data_permission_diff_log}，{@code EXPECTED_FILTER} 正向样本是
 * {@code enforce-readiness} 放开某个资源的硬前置。</p>
 *
 * <h2>J4 与 J2 之前的区别（这段是删掉 JPA 的那一半）</h2>
 * <p>J3 之前这里数三遍：{@code EntityManager} 数不带条件的、数带条件的（JPA Criteria 编译的
 * {@code Specification}），再把同一棵树交给 Mapper 侧的 {@code countByPermissionFragment} 数第三遍，
 * 拿"两套 ORM 的行数"互相印证。J4 把 {@code spring-boot-starter-data-jpa} 整体删掉之后：</p>
 * <ul>
 *   <li><b>第二遍不再有第二个 ORM</b>：JPA 侧与 Mapper 侧都换成同一套渲染器 + 同一棵条件树，
 *       再比"两边行数一致"就成了拿一把尺子量自己（§10.5 的口径：换完只剩一条路径）；</li>
 *   <li><b>但第一问必须留着</b>：它不是"两个 ORM 的差异"，是"加不加条件差多少行"，
 *       与 ORM 无关，而且是 {@code EXPECTED_FILTER} 样本的唯一来源 —— 一起删就等于
 *       把"逐资源放开"这条流程的证据链抽掉；</li>
 *   <li><b>两次 COUNT 用的是同一个仓储</b>（{@link EntityRepositories#bindingOf} 按实体类型解析，
 *       不按类名推表名 —— 那是 §19.34 写进基类注释的坑）。解析不到仓储时记
 *       {@code diff:no-repository} 并跳过：<b>"没有证据"与"一致"是两种结论</b>，不许混。</li>
 * </ul>
 *
 * <p>三条纪律与 J2 之前一致：</p>
 * <ol>
 *   <li><b>采样</b>：默认 2%（{@code kubo.data-permission.shadow.sample-rate}），
 *       双跑是额外两次 COUNT，不采样就是在拿生产 QPS 换观测；</li>
 *   <li><b>异步 + 有界队列</b>：比对永不占用业务线程；队列满时<b>丢任务并打点</b>，不阻塞、不抛给业务；</li>
 *   <li><b>只在 SHADOW 生效</b>：OFF 不参与，ENFORCE 下条件已经真注入，再双跑纯属浪费。</li>
 * </ol>
 *
 * <p>身份（userId / tenantId）在业务线程上取成值再带进异步任务：后台线程去读
 * {@code PermissionContextHolder} 只会读到"上一个占用该线程的请求"的身份。</p>
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
    private final EntityRepositories entityRepositories;
    private final ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider;

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
                             EntityRepositories entityRepositories,
                             ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider) {
        this.properties = properties;
        this.audit = audit;
        this.entityRepositories = entityRepositories;
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
     * @param resourceKey 资源标识
     * @param action      动作
     * @param entityClass 被过滤的实体类（两次 COUNT 都数它）
     * @param fragments   enforce 后<b>本应</b>注入的条件；空表示本轮没有可比的东西（不提交）
     * @param context     主体上下文（只用于记录是谁的查询）
     */
    public void compare(String resourceKey,
                        DataPermissionAction action,
                        Class<?> entityClass,
                        List<RowPermissionPredicate> fragments,
                        PermissionContext context) {
        if (entityClass == null || Object.class.equals(entityClass) || fragments == null || fragments.isEmpty()) {
            // 没有条件就没有"加条件会少行"这件事：提交上去只会得到一条恒为 CONSISTENT 的假证据
            return;
        }
        if (!sampled(resourceKey)) {
            return;
        }
        Long userId = context == null ? null : context.userId();
        Long tenantId = context == null ? null : context.tenantId();
        // 身份与条件都在业务线程上取好再带进异步任务；后台线程不读取 PermissionContextHolder。
        try {
            executor.execute(() -> doCompare(resourceKey, action, entityClass, List.copyOf(fragments), userId, tenantId));
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
                   List<RowPermissionPredicate> fragments,
                   Long userId,
                   Long tenantId) {
        long started = System.currentTimeMillis();
        Long baseline = null;
        Long guarded = null;
        String detail = null;
        try (PermissionContextHolder.Scope ignored = DataPermissionProbeContext.open()) {
            // 这两次数数自带条件，必须绕开拦截器：见 DataPermissionProbeContext 的类注释
            baseline = count(entityClass, List.of());
            guarded = count(entityClass, fragments);
        }
        catch (RuntimeException ex) {
            // 比对失败多数是"列不存在"或"实体没有仓储"，与 binding 指标同源，正好互相印证
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
            audit.shadow(resourceKey, "diff:" + verdict,
                    "baseline=" + baseline + " guarded=" + guarded + " cost=" + cost + "ms");
        }
    }

    /**
     * 按实体仓储数一次行数
     *
     * @param entityClass 实体类
     * @param fragments   要挂上去的权限条件（空 = 不带条件）
     * @return 行数（空结果记 0，不返回 null）
     * @apiNote 片段走 {@link RowPermissionPredicate#forQueryWrapper()}：与生产注入是<b>同一个</b>
     * 校验/换写出口（禁记号、参数必须被引用、只许标量都在那里），所以对账数出来的条件与
     * enforce 会注入的条件不可能不是同一份。
     */
    private Long count(Class<?> entityClass, List<RowPermissionPredicate> fragments) {
        EntityRepositories.Binding binding = entityRepositories.bindingOf(entityClass);
        if (binding == null) {
            // 不猜表名：按类名驼峰转下划线得到的是 contract_entity，而真表是 contract —— 那是另一种表
            throw new IllegalStateException("实体 " + entityClass.getSimpleName() + " 没有对应的 ICurdRepository");
        }
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        for (RowPermissionPredicate fragment : fragments) {
            RowPermissionPredicate.ApplyFragment apply = fragment.forQueryWrapper();
            wrapper.apply(apply.sql(), apply.values());
        }
        Long count = selectCount(binding.repository(), wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 仓储的泛型参数在解析器里已经擦成通配符，而行数查询与实体类型无关
     * （wrapper 只带列名与值）。这里用裸类型调用一次，语义就是"数这个实体"，见上方 {@link #count}。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Long selectCount(ICurdRepository<?> repository, QueryWrapper<Object> wrapper) {
        return ((ICurdRepository) repository).selectCount(wrapper);
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
            repository.insert(entity);
        }
        catch (RuntimeException ex) {
            log.debug("[data-permission][shadow] 差异落库失败：{}", ex.getMessage());
        }
    }
}
