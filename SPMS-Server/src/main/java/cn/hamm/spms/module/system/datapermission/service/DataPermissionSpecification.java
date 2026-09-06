package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleValidator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
    private final DataPermissionProperties properties;
    private final DataPermissionAudit audit;
    private final PermissionRuleParser ruleParser;
    private final JpaPermissionRuleCompiler ruleCompiler;

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
        if (predicates.isEmpty() && build.expectedConditions()) {
            audit.binding(resourceKey, DataPermissionAudit.REASON_BINDING_INVALID,
                    build.expectedConditions() + " 条策略无法绑定到实体列，检查 tenant_id/creator_id 是否在实体上映射");
            return enforced(mode) ? deny(builder, resourceKey, DataPermissionAudit.REASON_BINDING_INVALID, null) : List.of();
        }
        if (predicates.isEmpty()) {
            return List.of();
        }

        if (!enforced(mode)) {
            audit.shadow(resourceKey, "would-filter", describe(predicates));
            return List.of();
        }
        return predicates;
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
            boolean filtering = isFilteringSubject(policy.getSubjectType());
            Predicate predicate = buildPolicyPredicate(root, builder, resource, policy, context);
            if (predicate != null) {
                predicates.add(predicate);
            }
            else if (filtering) {
                expected++;
            }
        }

        return new BuildResult(predicates, expected);
    }

    /**
     * 该主体类型是否本该产生 SQL 过滤条件
     */
    private boolean isFilteringSubject(String subjectType) {
        if (subjectType == null) {
            return false;
        }
        return switch (subjectType) {
            case "TENANT", "USER", "DEPARTMENT", "FACTORY", "WAREHOUSE" -> true;
            // ROLE 由 getPolicies 侧过滤、SYSTEM 表示放行，都不该拼 SQL
            case "ROLE", "SYSTEM" -> false;
            default -> false;
        };
    }

    /**
     * 单条策略 = 「主体匹配条件」AND「condition_json 条件树」
     *
     * <p>历史上 {@code condition_json} 从来没被读过（列一直存在、值恒为 {@code {}}），
     * 于是"配了条件也不生效"。这里把它接上，并且<b>不静默跳过</b>非法条件：
     * 解析/校验异常会冒到 {@link #apply} 的统一兜底，enforce 下变成拒绝 + binding 指标。</p>
     */
    private <T> Predicate buildPolicyPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        Predicate subjectPredicate = buildSinglePredicate(root, builder, resource, policy, context);
        Predicate conditionPredicate = buildConditionPredicate(resource, policy, root, builder, context);
        if (subjectPredicate == null) {
            return conditionPredicate;
        }
        if (conditionPredicate == null) {
            return subjectPredicate;
        }
        return builder.and(subjectPredicate, conditionPredicate);
    }

    /**
     * 编译 condition_json 条件树
     */
    private <T> Predicate buildConditionPredicate(
            DataResourceEntity resource,
            DataPolicyEntity policy,
            Root<T> root,
            CriteriaBuilder builder,
            PermissionContext context
    ) {
        String json = policy.getConditionJson();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return null;
        }
        PermissionRule rule = ruleParser.parse(resource.getResourceKey(), json);
        if (!rule.contributes()) {
            return null;
        }
        new PermissionRuleValidator(allowListOf(resource)).validate(resource.getResourceKey(), rule);
        return ruleCompiler.compile(rule, root, builder, new cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext(context));
    }

    /**
     * 条件树可引用的列：归属列 + 显式登记的可过滤列（白名单为空时任何 field 都会被拒）
     */
    private PermissionRuleValidator.FieldAllowList allowListOf(DataResourceEntity resource) {
        java.util.Set<String> columns = new java.util.LinkedHashSet<>();
        addCsv(columns, resource.getTenantColumn());
        addCsv(columns, resource.getOwnerColumns());
        addCsv(columns, resource.getFilterableColumns());
        return new PermissionRuleValidator.FieldAllowList(columns);
    }

    private void addCsv(java.util.Collection<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                target.add(part.trim());
            }
        }
    }

    /**
     * 根据单个策略构建 JPA 谓词
     */
    private <T> Predicate buildSinglePredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        String subjectType = policy.getSubjectType();
        if (subjectType == null) {
            return null;
        }

        return switch (subjectType) {
            case "TENANT" -> buildTenantPredicate(root, builder, resource, context);
            case "USER" -> buildOwnerPredicate(root, builder, resource, context);
            case "DEPARTMENT" -> buildDepartmentPredicate(root, builder, resource, policy, context);
            case "FACTORY" -> buildFactoryPredicate(root, builder, resource, policy, context);
            case "WAREHOUSE" -> buildWarehousePredicate(root, builder, resource, policy, context);
            case "ROLE" -> null; // ROLE 策略通过 getPolicies 已过滤，无需额外 SQL 条件
            case "SYSTEM" -> null; // SYSTEM 策略允许所有，无需额外 SQL 条件
            default -> null;
        };
    }

    /**
     * 构建租户隔离谓词
     */
    private <T> Predicate buildTenantPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            PermissionContext context
    ) {
        String tenantColumn = resource.getTenantColumn();
        if (tenantColumn == null || tenantColumn.isBlank()) {
            return null;
        }
        try {
            return builder.equal(root.get(tenantColumn), context.tenantId());
        }
        catch (IllegalArgumentException ignored) {
            // 实体没有该字段时跳过；由调用方按策略计数暴露为 binding 异常
            return null;
        }
    }

    /**
     * 构建用户归属谓词
     */
    private <T> Predicate buildOwnerPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            PermissionContext context
    ) {
        String ownerColumns = resource.getOwnerColumns();
        if (ownerColumns == null || ownerColumns.isBlank()) {
            return null;
        }
        // ownerColumns 可以包含多个字段，用逗号分隔
        String[] columns = ownerColumns.split(",");
        List<Predicate> ownerPredicates = new ArrayList<>();
        for (String column : columns) {
            String trimmed = column.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ownerPredicates.add(builder.equal(root.get(trimmed), context.userId()));
            }
            catch (IllegalArgumentException ignored) {
                // 实体没有该字段时跳过
            }
        }
        if (ownerPredicates.isEmpty()) {
            return null;
        }
        if (ownerPredicates.size() == 1) {
            return ownerPredicates.get(0);
        }
        return builder.or(ownerPredicates.toArray(new Predicate[0]));
    }

    /**
     * 构建部门过滤谓词
     */
    private <T> Predicate buildDepartmentPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        if (policy.getSubjectId() == null || !context.departmentIds().contains(policy.getSubjectId())) {
            return null;
        }
        String departmentColumn = resolveDepartmentColumn(resource);
        if (departmentColumn == null) {
            return null;
        }
        try {
            return builder.equal(root.get(departmentColumn), policy.getSubjectId());
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 构建工厂过滤谓词
     */
    private <T> Predicate buildFactoryPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        if (policy.getSubjectId() == null || !context.factoryIds().contains(policy.getSubjectId())) {
            return null;
        }
        try {
            return builder.equal(root.get("factoryId"), policy.getSubjectId());
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 构建仓库过滤谓词
     */
    private <T> Predicate buildWarehousePredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        if (policy.getSubjectId() == null || !context.warehouseIds().contains(policy.getSubjectId())) {
            return null;
        }
        try {
            return builder.equal(root.get("warehouseId"), policy.getSubjectId());
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 从资源定义中解析部门字段名
     */
    private String resolveDepartmentColumn(DataResourceEntity resource) {
        String ownerColumns = resource.getOwnerColumns();
        if (ownerColumns == null || ownerColumns.isBlank()) {
            return "departmentId";
        }
        return ownerColumns.split(",")[0].trim();
    }
}
