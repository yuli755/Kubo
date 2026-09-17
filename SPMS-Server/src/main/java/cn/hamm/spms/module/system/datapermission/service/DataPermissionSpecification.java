package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <h1>数据权限 JPA Specification 构建器</h1>
 *
 * <p>基于当前请求的 {@link PermissionContext} 和资源标识生成 JPA Criteria 的 {@link Predicate} 列表，
 * 供业务 Service 的 {@code addSearchPredicate} 叠加使用。</p>
 *
 * <p><b>模式语义</b>（{@code kubo.data-permission.mode}）：</p>
 * <ul>
 *   <li>{@code OFF} —— 直接短路，行为等价于改造前（回滚位）；</li>
 *   <li>{@code SHADOW} —— 计算条件与差异但<b>不改变查询结果</b>，只输出/打点，用于灰度观察；</li>
 *   <li>{@code ENFORCE} —— 真正注入；绑定失败（归属列不存在、资源未注册且 {@code deny-unregistered=true}）
 *       一律返回空结果集，<b>不再静默放行</b>。</li>
 * </ul>
 *
 * <p><b>历史缺陷（本次修复的动机）</b>：旧实现对"资源未注册""列不存在"都是
 * {@code catch → return 放行}，于是 24 个 Service 接了权限代码、真实表却没有归属列，
 * 结果是<b>结构性空转</b>且无任何告警。现在这类情况统一进
 * {@link DataPermissionAudit#binding} 指标，enforce 前必须清零。</p>
 *
 * <p>注意：JPA 侧只能取实体已映射的字段。{@code tenant_id/creator_id} 在数据库加列后，
 * 必须同时在实体（基类）上映射，否则这里会以 binding 指标暴露 —— 这正是期望的信号，
 * 不要试图用 catch 掩盖。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionSpecification {

    private final DataPermissionService dataPermissionService;
    // 兜底属性名（departmentId / warehouseId / factoryId）以前在这里和 DimensionBindingAnalyzer
    // 各写一遍、靠注释约定同值；§19.35 起统一收在 SubjectDimensionRules，本类不再有副本。

    private final DataPermissionProperties properties;
    private final DataPermissionAudit audit;
    private final JpaPermissionRuleCompiler ruleCompiler;
    private final PermissionRuleTreeBuilder ruleTreeBuilder;

    /**
     * 影子双跑（只观测，不参与判定）。
     *
     * <p>用字段注入 + {@code required = false} 而不是塞进构造器：本类已有 3 个单测直接 new 出来，
     * 加一个"永远只是观测"的依赖去逼它们各改一行不划算；而它缺 Bean 时的正确行为恰好就是"不跑"。
     * 判定链路上任何<em>会影响结果</em>的东西都不许按这个形状接 —— 这条区别见 {@code DataPermissionDenial}。</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ShadowDiffService shadowDiffService;

    /**
     * Spring 装配构造器；JPA 与 MyBatis 读侧共用同一个策略建树器。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public DataPermissionSpecification(
            DataPermissionService dataPermissionService,
            DataPermissionProperties properties,
            DataPermissionAudit audit,
            JpaPermissionRuleCompiler ruleCompiler,
            PermissionRuleTreeBuilder ruleTreeBuilder) {
        this.dataPermissionService = dataPermissionService;
        this.properties = properties;
        this.audit = audit;
        this.ruleCompiler = ruleCompiler;
        this.ruleTreeBuilder = ruleTreeBuilder;
    }

    /**
     * 保留给纯 JPA 单测的最小构造器；生产装配必须走上面的共享建树器。
     */
    public DataPermissionSpecification(
            DataPermissionService dataPermissionService,
            DataPermissionProperties properties,
            DataPermissionAudit audit,
            cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser ruleParser,
            JpaPermissionRuleCompiler ruleCompiler) {
        this(dataPermissionService, properties, audit, ruleCompiler,
                new PermissionRuleTreeBuilder(ruleParser));
    }

    /**
     * 为指定资源构建数据权限过滤条件
     *
     * @param root        JPA Root
     * @param builder     JPA CriteriaBuilder
     * @param resourceKey 数据资源标识（如 {@code wms.inventory}）
     * @param action      数据操作类型
     * @param <T>         实体类型
     * @return 权限过滤谓词列表；空列表表示不追加条件
     */
    public <T> List<Predicate> buildPredicates(
            Root<T> root,
            CriteriaBuilder builder,
            String resourceKey,
            DataPermissionAction action
    ) {
        DataPermissionProperties.Mode mode = properties.effectiveMode(resourceKey);
        if (mode == DataPermissionProperties.Mode.OFF) {
            return List.of();
        }
        long started = System.nanoTime();
        try {
            return apply(root, builder, resourceKey, action, mode);
        }
        catch (RuntimeException ex) {
            // 权限计算自身异常属于安全事件：ENFORCE 拒绝，SHADOW 记录后放行
            audit.binding(resourceKey, DataPermissionAudit.REASON_PARSE_FAILED, ex.getMessage());
            return enforced(mode) ? deny(builder, resourceKey, DataPermissionAudit.REASON_PARSE_FAILED, ex.getMessage()) : List.of();
        }
        finally {
            audit.recordDecisionNanos(resourceKey, System.nanoTime() - started);
        }
    }

    private <T> List<Predicate> apply(
            Root<T> root,
            CriteriaBuilder builder,
            String resourceKey,
            DataPermissionAction action,
            DataPermissionProperties.Mode mode
    ) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            // 无上下文 = 定时任务/内部调用。不放大权限（不注入），但必须显式记账：
            // 这类调用点应改为构造系统身份上下文，而不是长期依赖此旁路。
            audit.binding(resourceKey, "no-permission-context", "跳过注入，调用方应显式构造系统身份上下文");
            return List.of();
        }
        if (context.bypassDataPermission()) {
            return List.of();
        }

        DataResourceEntity resource;
        try {
            resource = dataPermissionService.requireResource(resourceKey);
        }
        catch (RuntimeException ex) {
            if (enforced(mode) && properties.isDenyUnregistered()) {
                return deny(builder, resourceKey, DataPermissionAudit.REASON_UNREGISTERED, ex.getMessage());
            }
            audit.binding(resourceKey, DataPermissionAudit.REASON_UNREGISTERED, ex.getMessage());
            return List.of();
        }

        List<DataPolicyEntity> policies = dataPermissionService.getPolicies(resourceKey, action, context);

        if (policies.isEmpty()) {
            if (dataPermissionService.shouldDenyByDefault(resourceKey, context)) {
                if (enforced(mode)) {
                    return deny(builder, resourceKey, DataPermissionAudit.REASON_NO_POLICY, "无 QUERY 策略且资源要求默认拒绝");
                }
                audit.shadow(resourceKey, DataPermissionAudit.REASON_NO_POLICY, "enforce 后将返回空结果");
            }
            return List.of();
        }

        BuildResult build = buildPredicatesFromPolicies(root, builder, resource, policies, context);
        List<Predicate> predicates = build.predicates();

        // 只有「本该产生 SQL 条件却一条都没绑上」才算绑定失败；
        // SYSTEM / ROLE 策略语义是"允许全部"，生成不出条件是正确结果，不能误判为异常。
        // expectedConditions() 是 int 计数（"本该产生几条条件"），不是一眼看上去像布尔的开关；
        // 这里必须显式比较，写成 `&& build.expectedConditions()` 是 javac 级别的错误。
        if (predicates.isEmpty() && build.expectedConditions() > 0) {
            audit.binding(resourceKey, DataPermissionAudit.REASON_BINDING_INVALID,
                    build.expectedConditions() + " 条策略无法绑定到实体列，检查 tenant_id/creator_id 是否在实体上映射");
            return enforced(mode) ? deny(builder, resourceKey, DataPermissionAudit.REASON_BINDING_INVALID, null) : List.of();
        }
        if (predicates.isEmpty()) {
            return List.of();
        }

        if (!enforced(mode)) {
            audit.shadow(resourceKey, "would-filter", describe(predicates));
            submitShadowDiff(root, resourceKey, action, resource, policies, context, build.ruleTrees());
            return List.of();
        }
        return predicates;
    }

    /**
     * 把「enforce 下本应注入的条件」交给影子比对（采样、异步、异常只记账都在 {@link ShadowDiffService} 里）
     *
     * <p>这一步的意义不在"多看一眼日志"：{@code enforce-readiness} 的放开前置要求差异表里有
     * {@code EXPECTED_FILTER} 正向样本（证明"加条件确实会少行"），而<em>全仓只有这里能产生那些行</em>。
     * 接线断着的后果不是日志少几行，是"逐资源放开"这条流程永远停在"无证据"上 ——
     * 一个只读面板把自己的判据建在了一张没有生产者的表上（§19.30 欠的那条就是这里）。</p>
     *
     * <p>条件不在这儿重写一遍：直接把 {@link #buildPredicatesFromPolicies} 包成一个
     * {@link Specification}，比对侧拿到的就是 enforce 会注入的同一份条件。JPA 侧比"加不加条件"，
     * Mapper 侧比"同一棵树换个渲染方向"，所以这里额外把 {@code BuildResult#ruleTrees} 交出去 ——
     * 两个消费方读的都是同一次建树的产物，§19.35 之后这条链上再没有第二处会算"谁能看哪些行"。</p>
     */
    private <T> void submitShadowDiff(Root<T> root,
                                      String resourceKey,
                                      DataPermissionAction action,
                                      DataResourceEntity resource,
                                      List<DataPolicyEntity> policies,
                                      PermissionContext context,
                                      List<PermissionRule> ruleTrees) {
        if (shadowDiffService == null) {
            return;
        }
        // 投影查询 / tuple root 没有可 COUNT 的实体，拿不到实体类就跳过：
        // 让异步任务去 NPE，等于把一次观测变成一条没人认领的错误日志
        Class<?> entityClass = root.getJavaType();
        if (entityClass == null || Object.class.equals(entityClass)) {
            return;
        }
        Specification<T> guardedFilter = (filterRoot, filterQuery, filterBuilder) -> {
            List<Predicate> inner = buildPredicatesFromPolicies(filterRoot, filterBuilder, resource, policies,
                    context).predicates();
            return inner.isEmpty() ? filterBuilder.conjunction()
                    : filterBuilder.and(inner.toArray(new Predicate[0]));
        };
        // 对账用的是刚建好的那批树，不在比对侧按策略重算一遍：重算就是第四个判据现场
        // （表名同理，取登记的 table_name，不从实体名推 —— 那是 §19.34 写进基类注释的坑）。
        // 树为空时传 null：比对侧会把"不参与对账"读成"没证据"，而不是"一致"。
        ShadowDiffService.OrmParityProbe probe = ruleTrees == null || ruleTrees.isEmpty()
                ? null
                : new ShadowDiffService.OrmParityProbe(resource.getTableName(), ruleTrees, context);
        shadowDiffService.compare(resourceKey, action, entityClass, guardedFilter, context, probe);
    }

    /**
     * 便于单测注入（生产由容器装配；缺 Bean 就是不跑比对）
     *
     * @param shadowDiffService 影子双跑服务
     */
    void setShadowDiffService(ShadowDiffService shadowDiffService) {
        this.shadowDiffService = shadowDiffService;
    }

    /**
     * 默认拒绝：返回恒假谓词，而不是放开原查询
     */
    private <T> List<Predicate> deny(CriteriaBuilder builder, String resourceKey, String reason, String detail) {
        audit.deny(resourceKey, reason, detail);
        return List.of(builder.disjunction());
    }

    private boolean enforced(DataPermissionProperties.Mode mode) {
        return mode == DataPermissionProperties.Mode.ENFORCE;
    }

    private String describe(List<Predicate> predicates) {
        return predicates.stream().map(Predicate::toString).collect(Collectors.joining(" AND "));
    }

    /**
     * 构建结果
     *
     * @param predicates         实际生成的谓词
     * @param expectedConditions 本该生成 SQL 条件的策略条数（用于识别"绑不上列"的空转）
     * @param ruleTrees          与 predicates 同一批的<em>条件树</em>（未编译的形状），Mapper 侧对账用它；
     *                           两者条数可以不等 —— 某条树在 JPA 侧绑不上列时会少一条谓词，
     *                           而 SQL 侧照样把它拼进片段 ⇒ 两边行数必然不同（对账会记成 MAPPER_LOOSE，
     *                           也就是"Mapper 多放行"，那是换轨前必须解释掉的形状）
     */
    private record BuildResult(List<Predicate> predicates, int expectedConditions, List<PermissionRule> ruleTrees) {
    }

    /**
     * 从策略列表构建 JPA 谓词
     */
    private <T> BuildResult buildPredicatesFromPolicies(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            List<DataPolicyEntity> policies,
            PermissionContext context
    ) {
        List<Predicate> predicates = new ArrayList<>();
        List<PermissionRule> ruleTrees = new ArrayList<>(policies.size());
        int expected = 0;

        for (PermissionRuleTreeBuilder.PolicyRule policyRule : ruleTreeBuilder.build(resource, policies, context)) {
            PermissionRule tree = policyRule.rule();
            if (tree != null) {
                ruleTrees.add(tree);
                Predicate predicate = ruleCompiler.compile(tree, root, builder, new PermissionRuleContext(context));
                if (predicate != null) {
                    predicates.add(predicate);
                }
                else if (policyRule.shouldBind()) {
                    expected++;
                }
            }
            else if (policyRule.shouldBind()) {
                expected++;
            }
        }

        return new BuildResult(predicates, expected, List.copyOf(ruleTrees));
    }

}
