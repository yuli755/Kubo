package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h1>维度可绑定性盘点：这个资源上某档策略有没有列可绑</h1>
 *
 * <p>与 {@link RowPolicyRiskAnalyzer} 是同一类问题、不同一层事实：那边问"这一行策略会不会被读到"，
 * 这里问"就算读到了，它要的列在实体上存在吗"。两者都要盘，因为运行期对这两种情况的处理<b>一样安静</b>：
 * {@code DataPermissionSpecification#apply} 里"本该产生条件却没绑上"只写进审计（binding 指标），
 * enforce 下退化成恒假（该主体一行也查不到）、shadow 下等价于没配。界面上既不报错也不变黄。</p>
 *
 * <h2>判据，全部锚在拼谓词那几行</h2>
 * <ol>
 *   <li>{@code TENANT} —— {@code buildTenantPredicate} 用 {@code resource.tenantColumn}（缺省 {@code tenant_id}）；
 *       登记名按 {@link PermissionRuleSupport#pathCandidates} 的候选写法都取不到 ⇒ 绑不上；</li>
 *   <li>{@code USER} —— {@code buildOwnerPredicate} 逐个试 {@code owner_columns}（逗号分隔），
 *       <b>任一项能绑就算能绑</b>，所以这里也必须按项判定，不能把整串当名字；</li>
 *   <li>{@code DEPARTMENT} —— {@code resolveDepartmentColumn} 取的是 {@code owner_columns} 的<b>第一项</b>；
 *       于是同一份登记同时被当"负责人列"和"部门列"用，两种语义不可能同时成立，单列一条报出来；</li>
 *   <li>{@code WAREHOUSE} / {@code FACTORY} —— 属性名在代码里写死（{@code warehouseId} / {@code factoryId}），
 *       资源登记管不到它们；实体上若只有 {@code @ManyToOne storage} 这类关联，那不是配置写错，而是缺关联路径
 *       支持（施工图 D1(b)），详情里分开说。</li>
 * </ol>
 *
 * <p><b>只在"这一档确实有启用策略"时才报</b>：没配 FACTORY 策略的资源被报"工厂维度绑不上"是纯噪音，
 * 噪音会让整个面板失去可信度。启动期该拦的（D13 就绪度）也不在这里重复拦。</p>
 *
 * <p>本类不查库、不读元模型、不判灰度模式；形状由调用方给出（与 {@code FieldPolicyGapAnalyzer} 同一套做法）。</p>
 *
 * @author Hamm.cn
 */
public final class DimensionBindingAnalyzer {

    /**
     * 资源登记的表名在 JPA 元模型里找不到实体（所有维度都无从绑起）
     */
    public static final String KIND_ENTITY_NOT_MAPPED = "ENTITY_NOT_MAPPED";

    /**
     * 租户列在实体上不存在或未登记
     */
    public static final String KIND_TENANT_UNBOUNDABLE = "TENANT_COLUMN_UNBOUNDABLE";

    /**
     * 负责人列（owner_columns）未登记或全都在实体上取不到
     */
    public static final String KIND_OWNER_UNBOUNDABLE = "OWNER_COLUMN_UNBOUNDABLE";

    /**
     * 部门列取不到
     */
    public static final String KIND_DEPARTMENT_UNBOUNDABLE = "DEPARTMENT_COLUMN_UNBOUNDABLE";

    /**
     * 部门列与负责人列是同一列：两种语义不可能同时成立
     */
    public static final String KIND_DEPARTMENT_COLUMN_SHARED = "DEPARTMENT_COLUMN_SHARED";

    /**
     * 仓库维度：写死的属性名在实体上没有
     */
    public static final String KIND_WAREHOUSE_UNBOUNDABLE = "WAREHOUSE_COLUMN_UNBOUNDABLE";

    /**
     * 工厂维度：写死的属性名在实体上没有
     */
    public static final String KIND_FACTORY_UNBOUNDABLE = "FACTORY_COLUMN_UNBOUNDABLE";

    /**
     * 仓库档在 {@code DataPermissionSpecification} 里写死的属性名
     */
    static final String WAREHOUSE_ATTRIBUTE = "warehouseId";

    /**
     * 工厂档写死的属性名
     */
    static final String FACTORY_ATTRIBUTE = "factoryId";

    /**
     * {@code resolveDepartmentColumn} 在 owner_columns 为空时用的缺省属性名
     */
    static final String DEPARTMENT_DEFAULT_ATTRIBUTE = "departmentId";

    /**
     * 各维度在实体上"可能的关联名"
     *
     * <p>存在的意义是把两类问题分开：{@code warehouseId} 找不到，如果实体上其实有
     * {@code @ManyToOne storage}，那要说的是"缺关联路径支持（D1(b)）"，而不是"你名字写错了"。
     * 混成一句，运营就会去改登记名，而改完还是绑不上。</p>
     */
    private static final Map<String, Set<String>> ASSOCIATION_ALIASES = Map.of(
            DataPermissionSubjectType.WAREHOUSE.name(), Set.of("storage", "warehouse", "storageId"),
            DataPermissionSubjectType.FACTORY.name(), Set.of("factory", "factoryId"),
            DataPermissionSubjectType.DEPARTMENT.name(), Set.of("department", "dept"),
            DataPermissionSubjectType.USER.name(), Set.of("creator", "owner", "user"),
            DataPermissionSubjectType.TENANT.name(), Set.of("tenant"));

    /**
     * 一个资源的可绑定性输入
     *
     * @param resourceId    资源 ID
     * @param resourceKey   资源标识
     * @param tableName     登记的物理表名（仅用于详情）
     * @param tenantColumn  登记的租户列
     * @param ownerColumns  登记的归属列（逗号分隔）
     * @param attributes    实体上的标量属性名；空集合表示没有映射
     * @param associations    实体上的关系属性名
     * @param policyCounts  该资源上<b>启用中的</b>行级策略按主体类型的条数（决定"这档要不要报"）
     */
    public record Row(Long resourceId,
                      String resourceKey,
                      String tableName,
                      String tenantColumn,
                      String ownerColumns,
                      Set<String> attributes,
                      Set<String> associations,
                      Map<String, Long> policyCounts) {
    }

    /**
     * 一条盘点结论
     *
     * @param resourceId   资源 ID
     * @param resourceKey  资源标识
     * @param subjectType  受影响的维度；{@link #KIND_ENTITY_NOT_MAPPED} 时为 {@code null}（整表无映射）
     * @param kind         类别常量
     * @param detail       人话说明（含建议写法；前端只渲染，不再判语义）
     */
    public record Risk(Long resourceId, String resourceKey, String subjectType, String kind, String detail) {
    }

    private DimensionBindingAnalyzer() {
    }

    /**
     * 逐资源逐维度判定
     *
     * @param rows 资源形状与"每档在用几条策略"
     * @return 风险清单；无问题为空列表
     */
    public static List<Risk> risks(Collection<Row> rows) {
        List<Risk> risks = new ArrayList<>();
        if (rows == null) {
            return risks;
        }
        for (Row row : rows) {
            if (row == null) {
                continue;
            }
            Set<String> attributes = row.attributes() == null ? Set.of() : row.attributes();
            Set<String> associations = row.associations() == null ? Set.of() : row.associations();
            Map<String, Long> counts = row.policyCounts() == null ? Map.of() : row.policyCounts();
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            if (attributes.isEmpty()) {
                risks.add(new Risk(row.resourceId(), row.resourceKey(), null, KIND_ENTITY_NOT_MAPPED,
                        "登记的表名「" + safe(row.tableName()) + "」在 JPA 元模型里找不到对应实体 ⇒ 该资源上 "
                                + total + " 条启用策略都没有列可绑：enforce 下落到「绑定失败」恒假（查不到数据），"
                                + "shadow 下只记审计。先确认表名是否写错、实体是否改了名，或该模块尚未接入。"));
                continue;
            }
            EntityShapeIndex.Shape shape = new EntityShapeIndex.Shape(null, attributes, associations);

            check(risks, row, shape, DataPermissionSubjectType.TENANT.name(), counts, KIND_TENANT_UNBOUNDABLE,
                    "租户列", row.tenantColumn(), true);
            check(risks, row, shape, DataPermissionSubjectType.USER.name(), counts, KIND_OWNER_UNBOUNDABLE,
                    "负责人列", row.ownerColumns(), false);
            department(risks, row, shape, counts);
            check(risks, row, shape, DataPermissionSubjectType.WAREHOUSE.name(), counts, KIND_WAREHOUSE_UNBOUNDABLE,
                    "仓库列（属性名在代码里写死，资源登记管不到）", WAREHOUSE_ATTRIBUTE, false);
            check(risks, row, shape, DataPermissionSubjectType.FACTORY.name(), counts, KIND_FACTORY_UNBOUNDABLE,
                    "工厂列（属性名在代码里写死，资源登记管不到）", FACTORY_ATTRIBUTE, false);
        }
        return risks;
    }

    /**
     * 部门档单独处理：它读的是 owner_columns 的第一项，因此可能与负责人档抢同一列
     */
    private static void department(List<Risk> risks, Row row, EntityShapeIndex.Shape shape, Map<String, Long> counts) {
        long using = count(counts, DataPermissionSubjectType.DEPARTMENT.name());
        if (using <= 0) {
            return;
        }
        String first = firstOwnerColumn(row.ownerColumns());
        boolean usesDefault = first == null;
        String column = usesDefault ? DEPARTMENT_DEFAULT_ATTRIBUTE : first;
        // 与负责人档共用同一列：两个谓词拿同一列比不同的值，不可能同时对
        if (!usesDefault && csv(row.ownerColumns()).size() == 1
                && count(counts, DataPermissionSubjectType.USER.name()) > 0) {
            risks.add(new Risk(row.resourceId(), row.resourceKey(), DataPermissionSubjectType.DEPARTMENT.name(),
                    KIND_DEPARTMENT_COLUMN_SHARED,
                    "owner_columns 只登记了「" + column + "」一项，而它同时被两档使用：负责人策略拿它比用户 ID、"
                            + "部门策略拿它比部门 ID ⇒ 两档里必有一档比出错值（绑得上，所以也不会进绑定失败分支，"
                            + "连审计都不记）。要么给部门单独登记一列，要么别再对这类资源配 DEPARTMENT 策略；"
                            + "根治办法是让部门列能独立登记（现在只能靠 owner_columns 的第一项）。"));
            return;
        }
        check(risks, row, shape, DataPermissionSubjectType.DEPARTMENT.name(), counts, KIND_DEPARTMENT_UNBOUNDABLE,
                usesDefault ? "部门列（未登记 owner_columns 时按 departmentId 找）" : "部门列", column, false);
    }

    /**
     * 一档判定的公共形状：没登记与"登记了但取不到"分开说，前者是漏配、后者是配错或形状不对
     */
    private static void check(List<Risk> risks, Row row, EntityShapeIndex.Shape shape, String subjectType,
                              Map<String, Long> counts, String kind, String what, String registered,
                              boolean missingIsConfigError) {
        long using = count(counts, subjectType);
        if (using <= 0) {
            return;
        }
        List<String> names = csv(registered);
        if (names.isEmpty()) {
            risks.add(new Risk(row.resourceId(), row.resourceKey(), subjectType, kind,
                    "该资源上有 " + using + " 条 " + subjectType + " 策略，但" + what + "没有登记 ⇒ 这一档拼不出条件。"
                            + (missingIsConfigError ? "（未登记时按 tenant_id 找，而实体上通常是属性 tenantId，"
                            + "所以租户隔离要么显式登记，要么依赖运行期的列名兜底）"
                            : "请把实体上真实存在的属性名登记进来（列名与属性名两种写法运行期都认，但必须对得上）。")));
            return;
        }
        for (String name : names) {
            if (shape.binds(name)) {
                return;
            }
        }
        String association = findAssociation(shape, names, subjectType);
        StringBuilder detail = new StringBuilder();
        detail.append("实体上找不到").append(what).append("「").append(String.join("」/「", names)).append("」")
                .append("（每种登记名都会按 ").append(PermissionRuleSupport.pathCandidates(names.get(0)))
                .append(" 试一遍） ⇒ ").append(subjectType).append(" 那 ").append(using)
                .append(" 条策略拼不出条件：enforce 下恒假（该主体查不到数据），shadow 下等于没配。");
        if (association != null) {
            detail.append(" 注意 ").append(association).append(" 在实体上是关联而不是标量列：这不是登记写错，")
                    .append("而是缺关联路径支持（施工图 D1(b)：需要 root.join(「").append(association)
                    .append("」).get(「id」) 这种形状），补上之前这一档不可能生效。");
        }
        risks.add(new Risk(row.resourceId(), row.resourceKey(), subjectType, kind, detail.toString()));
    }

    /**
     * 先按登记名的候选写法找关联，再按该维度的常见关联名找（{@code warehouseId} 找不到但
     * {@code storage} 在，是本项目里最常见的一种"形状不对"）
     */
    private static String findAssociation(EntityShapeIndex.Shape shape, List<String> names, String subjectType) {
        for (String name : names) {
            String hit = shape.associationNamedLike(name);
            if (hit != null) {
                return hit;
            }
        }
        for (String alias : ASSOCIATION_ALIASES.getOrDefault(subjectType, Set.of())) {
            if (shape.associations().contains(alias)) {
                return alias;
            }
        }
        return null;
    }

    /**
     * 逗号分隔的登记值 → 逐项（与 {@code buildOwnerPredicate} 的解析方式一致）
     */
    private static List<String> csv(String raw) {
        List<String> parts = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return parts;
        }
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    private static String firstOwnerColumn(String ownerColumns) {
        List<String> parts = csv(ownerColumns);
        return parts.isEmpty() ? null : parts.get(0);
    }

    private static long count(Map<String, Long> counts, String subjectType) {
        Long value = counts.get(subjectType);
        return value == null ? 0L : value;
    }

    private static String safe(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /**
     * 供调用方复用：把策略行的主体类型收成"每档几条"
     *
     * <p>这里不判"什么算合法主体"，那是 {@link RowPolicyRiskAnalyzer} 的事；重复判一次就会有两份口径。</p>
     *
     * @param subjectTypes 主体类型字符串，允许含 null / 空白
     * @return 按遇到顺序排列的计数表
     */
    public static Map<String, Long> countBySubjectType(Collection<String> subjectTypes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (subjectTypes == null) {
            return counts;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String type : subjectTypes) {
            if (type == null || type.isBlank()) {
                continue;
            }
            String trimmed = type.trim();
            seen.add(trimmed);
            counts.merge(trimmed, 1L, Long::sum);
        }
        return counts;
    }
}
