package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleOp;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleValidator;
import cn.hamm.spms.module.system.datapermission.rule.SubjectDimensionRules;
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
    private final PermissionRuleParser ruleParser;
    private final JpaPermissionRuleCompiler ruleCompiler;

    /**
     * 影子双跑（只观测，不参与判定）。
     *
     * <p>用字段注入 + {@code required = false} 而不是塞进构造器：本类已有 3 个单测直接 new 出来，
     * 加一个"永远只是观测"的依赖去逼它们各改一行不划算；而它缺 Bean 时的正确行为恰好就是"不跑"。
     * 判定链路上任何<em>会影响结果</em>的东西都不许按这个形状接 —— 这条区别见 {@code DataPermissionDenial}。</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ShadowDiffService shadowDiffService;

    public DataPermissionSpecification(
            DataPermissionService dataPermissionService,
            DataPermissionProperties properties,
            DataPermissionAudit audit,
            PermissionRuleParser ruleParser,
            JpaPermissionRuleCompiler ruleCompiler) {
        this.dataPermissionService = dataPermissionService;
        this.properties = properties;
        this.audit = audit;
        this.ruleParser = ruleParser;
        this.ruleCompiler = ruleCompiler;
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
            submitShadowDiff(root, resourceKey, action, resource, policies, context);
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
     * {@link Specification}，比对侧拿到的就是 enforce 会注入的同一份条件。</p>
     */
    private <T> void submitShadowDiff(Root<T> root,
                                      String resourceKey,
                                      DataPermissionAction action,
                                      DataResourceEntity resource,
                                      List<DataPolicyEntity> policies,
                                      PermissionContext context) {
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
        shadowDiffService.compare(resourceKey, action, entityClass, guardedFilter, context);
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
     */
    private record BuildResult(List<Predicate> predicates, int expectedConditions) {
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
        int expected = 0;

        for (DataPolicyEntity policy : policies) {
            PolicyBuild build = buildPolicyBuild(root, builder, resource, policy, context);
            if (build.predicate() != null) {
                predicates.add(build.predicate());
            }
            else if (build.shouldBind()) {
                // 「本该拼出条件却没拼出来」——计数口径没变，变的只是判据住在哪：
                // 以前由本类的 isFilteringSubject 判断，现在由规则侧在生成条件的那一刻一并给出，
                // 于是"哪些主体类型参与行级过滤"这句话不会在这里和归属工厂里各说一遍
                expected++;
            }
        }

        return new BuildResult(predicates, expected);
    }

    /**
     * 一条策略 = 「主体维度条件」AND「condition_json 条件树」，两半先合成<em>一棵树</em>再编译
     *
     * <p>合一之前，这里是 {@code switch (subjectType)} 加五个 {@code buildXxxPredicate} 直接拼
     * Criteria，而 SQL 侧另有一份 {@code RowPermissionPredicate} 工厂写着同一件事。两份实现各改一次
     * 就分家，而分家的表现不是报错：JPA 少滤一点或多滤一点，Mapper 侧另一个数，谁都不响。
     * 现在主体维度只由 {@link SubjectDimensionRules} 回答一次，
     * {@link JpaPermissionRuleCompiler} 与 {@code SqlPermissionRuleRenderer} 各自渲染同一棵树
     * （§19.35）。</p>
     *
     * <p>白名单校验只覆盖 {@code condition_json} 那棵树：归属候选来自 {@code data_resource} 登记，
     * 它们<em>允许</em>指向实体上没有的列 —— 那正是"该列没映射"这个缺陷被数出来的方式，
     * 拿白名单去校验它，缺陷就变成一句"非法字段"，面板从此看不见。</p>
     */
    private <T> PolicyBuild buildPolicyBuild(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        SubjectDimensionRules.SubjectRule subject = SubjectDimensionRules.forPolicy(resource, policy, context);
        PermissionRule condition = parseConditionRule(resource, policy);

        List<PermissionRule> parts = new ArrayList<>(2);
        if (subject.rule() != null && subject.rule().contributes()) {
            parts.add(subject.rule());
        }
        if (condition != null) {
            parts.add(condition);
        }
        if (parts.isEmpty()) {
            return new PolicyBuild(null, subject.shouldBind());
        }
        PermissionRule combined = parts.size() == 1
                ? parts.get(0)
                : PermissionRule.group(PermissionRuleOp.ALL_OF, parts);
        Predicate predicate = ruleCompiler.compile(combined, root, builder, new PermissionRuleContext(context));
        return new PolicyBuild(predicate, subject.shouldBind());
    }

    /**
     * 解析并校验 condition_json，产出条件树（不做编译：编译发生在与主体条件合并之后）
     *
     * <p>历史上 {@code condition_json} 从来没被读过（列一直存在、值恒为 {@code {}}），
     * 于是"配了条件也不生效"。这里把它接上，并且<b>不静默跳过</b>非法条件：
     * 解析/校验异常会冒到 {@link #apply} 的统一兜底，enforce 下变成拒绝 + binding 指标。</p>
     */
    private PermissionRule parseConditionRule(DataResourceEntity resource, DataPolicyEntity policy) {
        String json = policy.getConditionJson();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return null;
        }
        PermissionRule rule = ruleParser.parse(resource.getResourceKey(), json);
        if (!rule.contributes()) {
            return null;
        }
        new PermissionRuleValidator(allowListOf(resource)).validate(resource.getResourceKey(), rule);
        return rule;
    }

    /**
     * 条件树可引用的列：归属列 + 显式登记的可过滤列（白名单为空时任何 field 都会被拒）
     */
    private PermissionRuleValidator.FieldAllowList allowListOf(DataResourceEntity resource) {
        // 合成规则只有一份（见 FieldAllowList.ofRegistrations）：读侧与写侧校验必须认同一批列，
        // 否则会出现「策略在查询里能过、在 update/delete 上被判非法字段」这种查得见却改不动的资源
        return PermissionRuleValidator.FieldAllowList.ofRegistrations(
                resource.getTenantColumn(), resource.getOwnerColumns(), resource.getFilterableColumns());
    }

    /**
     * 单条策略的构建结果
     *
     * @param predicate  编译出的 JPA 谓词；{@code null} 表示这一条不贡献条件
     * @param shouldBind 该主体类型本该拼出条件（用来把"结构性空转"数出来）
     */
    private record PolicyBuild(Predicate predicate, boolean shouldBind) {
    }
}
