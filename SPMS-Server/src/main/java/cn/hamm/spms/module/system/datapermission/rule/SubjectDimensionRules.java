package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;

import java.util.List;

/**
 * <h1>主体维度 → 条件树（行级判据的唯一出处）</h1>
 *
 * <p>本类只回答三件事：<b>这条策略属于哪个主体维度</b>、<b>该维度在资源上登记了哪些候选列</b>、
 * <b>拿什么值去比</b>。它不碰 JPA Criteria、也不拼 SQL —— 那两个渲染器
 * （{@link JpaPermissionRuleCompiler} / {@link SqlPermissionRuleRenderer}）读同一棵返回来的树。
 * 判据分家的代价不是"两边算得不一样"这么温和：写侧行级保护（{@code JpaRowVisibilityProbe}）
 * 与读侧共用 {@code DataPermissionSpecification}，而 Mapper 读端点迟早要用 SQL 那份，
 * 于是"查得到却改不动"或者反过来的问题会从两个方向轮流出现。</p>
 *
 * <h2>与 {@code DataPermissionSubjectMatcher} 的分工（各有一份 switch 是刻意的）</h2>
 * <p>那边回答"这条策略适不适用于当前主体"，本类回答"适用之后要拼什么条件"。
 * 两者对同一维度必须给出同一门槛判断（部门档就是靠复用 {@link PermissionContext#coversDepartment}
 * 做到这一点的），但把它们合成一个方法是错的：合完之后"策略可达但条件拼不出来"这类
 * 需要被单独数出来的状态就没了。</p>
 *
 * <h2>缺省属性名为什么写死在这里</h2>
 * <p>现网登记大量是"裸写法"（{@code owner_columns} 里没带 {@code DEPARTMENT:} 前缀），
 * 所以部门/工厂/仓库在没有显式登记时必须有一个历史约定的兜底名。这三个常量同时被
 * {@link cn.hamm.spms.module.system.datapermission.service.DimensionBindingAnalyzer} 用来生成
 * 面板提示语 —— 从本类出发，是为了让"兜底名改了、面板还在建议旧名字"不可能发生
 * （以前两处各写一遍字面量，靠注释约定同值）。</p>
 *
 * @author Hamm.cn
 */
public final class SubjectDimensionRules {

    /**
     * 部门档的缺省属性名
     */
    public static final String DEPARTMENT_ATTRIBUTE = "departmentId";

    /**
     * 工厂档的缺省属性名
     */
    public static final String FACTORY_ATTRIBUTE = "factoryId";

    /**
     * 仓库档的缺省属性名
     */
    public static final String WAREHOUSE_ATTRIBUTE = "warehouseId";

    private SubjectDimensionRules() {
    }

    /**
     * 一个主体维度的判定结果
     *
     * @param rule      条件（{@link PermissionRule.AllowAll} 表示这一维不贡献条件）
     * @param shouldBind 该主体类型<b>本该</b>拼出 SQL 条件：条件最后没拼出来时，上层要按"绑定失败"
     *                   计数（enforce 下恒假、shadow 下打 binding 指标）。
     *                   {@code ROLE}/{@code SYSTEM} 语义是"放行"或"不参与行级"，所以是 {@code false} ——
     *                   把它们算成绑定失败会让面板刷出一片假红灯
     */
    public record SubjectRule(PermissionRule rule, boolean shouldBind) {

        private static final SubjectRule NONE = new SubjectRule(PermissionRule.AllowAll.of(), false);

        /**
         * 这一维不参与行级过滤
         */
        public static SubjectRule none() {
            return NONE;
        }
    }

    /**
     * 按策略的 {@code subject_type} 生成归属条件
     *
     * @param resource 资源登记（提供 {@code tenant_column} 与 {@code owner_columns}）
     * @param policy   策略行（提供 {@code subject_type} 与 {@code subject_id}）
     * @param context  当前主体
     * @return 条件与"本该绑上"的标记；{@code null} 入参一律按 {@link SubjectRule#none()} 处理
     */
    public static SubjectRule forPolicy(DataResourceEntity resource, DataPolicyEntity policy, PermissionContext context) {
        if (resource == null || policy == null || context == null) {
            return SubjectRule.none();
        }
        String subjectType = policy.getSubjectType();
        if (subjectType == null) {
            return SubjectRule.none();
        }
        return switch (subjectType) {
            // 租户档：值来自上下文，可能是 null（无租户上下文）。这里刻意把值烤进树里，
            // 不写 contextVar：JPA 侧对 null 的既有语义是"与该列比 null"，换成 contextVar 会变成
            // "恒假"，那是两种可观测的结果，不该顺手夹在这次"只是合一"的重构里。
            case "TENANT" -> filtering(new PermissionRule.Dimension(PermissionRuleOp.EQ,
                    OwnerColumnRegistration.candidatesFor(resource.getOwnerColumns(),
                            DataPermissionSubjectType.TENANT.name(), single(resource.getTenantColumn())),
                    context.tenantId(), false));
            // 负责人档：多个裸写法的归属列是 OR 关系（一行只要属于我其中之一就算我的）
            case "USER" -> filtering(new PermissionRule.Dimension(PermissionRuleOp.EQ,
                    OwnerColumnRegistration.candidatesFor(resource.getOwnerColumns(),
                            DataPermissionSubjectType.USER.name(),
                            OwnerColumnRegistration.untagged(resource.getOwnerColumns())),
                    context.userId(), true));
            case "DEPARTMENT" -> {
                // 门槛判据与 DataPermissionSubjectMatcher 是同一个方法（收在 PermissionContext 上）：
                // 两处各写一遍就会出现"策略被判定可达、谓词却拼不出来"，那种不一致查起来极痛
                if (!context.coversDepartment(policy.getSubjectId())) {
                    yield filtering(PermissionRule.AllowAll.of());
                }
                yield filtering(new PermissionRule.Dimension(PermissionRuleOp.EQ,
                        departmentCandidates(resource), policy.getSubjectId(), false));
            }
            case "FACTORY" -> {
                if (policy.getSubjectId() == null || !context.factoryIds().contains(policy.getSubjectId())) {
                    // 主体不覆盖这条策略：与"绑不上列"同形（都是没条件），仍要计数 ——
                    // 走到这里说明判定侧认为它可达，那"拼不出来"就是真问题
                    yield filtering(PermissionRule.AllowAll.of());
                }
                yield filtering(new PermissionRule.Dimension(PermissionRuleOp.EQ,
                        OwnerColumnRegistration.candidatesFor(resource.getOwnerColumns(),
                                DataPermissionSubjectType.FACTORY.name(), List.of(FACTORY_ATTRIBUTE)),
                        policy.getSubjectId(), false));
            }
            case "WAREHOUSE" -> {
                if (policy.getSubjectId() == null || !context.warehouseIds().contains(policy.getSubjectId())) {
                    yield filtering(PermissionRule.AllowAll.of());
                }
                yield filtering(new PermissionRule.Dimension(PermissionRuleOp.EQ,
                        OwnerColumnRegistration.candidatesFor(resource.getOwnerColumns(),
                                DataPermissionSubjectType.WAREHOUSE.name(), List.of(WAREHOUSE_ATTRIBUTE)),
                        policy.getSubjectId(), false));
            }
            // ROLE 由取策略那一侧过滤、SYSTEM 表示放行：都不该拼 SQL
            case "ROLE", "SYSTEM" -> SubjectRule.none();
            default -> SubjectRule.none();
        };
    }

    /**
     * 部门档候选：{@code DEPARTMENT:} 显式项优先，其次沿用裸写法（历史约定是 owner_columns 第一项），
     * 两个都没有才落到写死的缺省属性名
     */
    public static List<String> departmentCandidates(DataResourceEntity resource) {
        List<String> registered = OwnerColumnRegistration.candidatesFor(resource.getOwnerColumns(),
                DataPermissionSubjectType.DEPARTMENT.name(),
                OwnerColumnRegistration.untagged(resource.getOwnerColumns()));
        return registered.isEmpty() ? List.of(DEPARTMENT_ATTRIBUTE) : registered;
    }

    private static SubjectRule filtering(PermissionRule rule) {
        return new SubjectRule(rule, true);
    }

    private static List<String> single(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim());
    }
}
