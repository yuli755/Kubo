package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.rule.SubjectDimensionRules;

import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.rule.OwnerColumnRegistration;
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
 *
 * <p>共同前提：{@code owner_columns} 现在按 {@link OwnerColumnRegistration} 的语法解析 —— 项内可带
 * {@code DIMENSION:} 前缀，也可写成 {@code storage.id} 这种一跳关联路径。<b>本类必须与
 * {@code DataPermissionSpecification} 用同一套回退顺序</b>（有前缀项 → 用它们；没有 → 该档既有取值），
 * 否则面板要么报一个运行期其实绑得上的资源，要么放过一个绑不上的；两种都比不报更糟。</p>
 * <ol>
 *   <li>{@code TENANT} —— 优先 {@code TENANT:xxx}，否则 {@code resource.tenantColumn}（缺省 {@code tenant_id}）；
 *       登记名按 {@link PermissionRuleSupport#pathCandidates} 的候选写法都取不到 ⇒ 绑不上；</li>
 *   <li>{@code USER} —— {@code USER:xxx}，否则全部裸写法项，<b>任一项能绑就算能绑</b>，
 *       所以这里也必须按项判定，不能把整串当名字；</li>
 *   <li>{@code DEPARTMENT} —— {@code DEPARTMENT:xxx}，否则裸写法的第一项，再否则 {@code departmentId}。
 *       只有"部门没有自己的登记"时，同一列才会同时被当负责人列与部门列用（两种语义不可能同时成立），
 *       那种形状单列一条报出来，并给出可抄的解法；</li>
 *   <li>{@code WAREHOUSE} / {@code FACTORY} —— {@code WAREHOUSE:xxx} / {@code FACTORY:xxx}，否则代码里写死的
 *       {@code warehouseId} / {@code factoryId}。实体上只有 {@code @ManyToOne storage} 时，登记
 *       {@code WAREHOUSE:storage.id} 即可绑上（D1(b) 已支持），详情里给出这个写法。</li>
 * </ol>
 *
 * <p>{@code owner_columns} 整串解析不出来时只报一条 {@link #KIND_OWNER_COLUMN_MALFORMED} 并跳过逐档判定：
 * 那种状态下各档只能拿写死的默认列去猜，报出来的是一串互相矛盾的猜测，真正的原因反倒被埋掉。</p>
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
     * 资源登记的表名在实体集合里找不到对应实体（所有维度都无从绑起）
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
     * owner_columns 整串解析不出来（前缀不是维度、路径超过一跳等）
     *
     * <p>单独立一类，而不是混进"某档绑不上"：格式错会让六档一起退化到写死的默认列，
     * 那时面板会刷六条互相矛盾的说法，唯独不说真正的原因。</p>
     */
    public static final String KIND_OWNER_COLUMN_MALFORMED = "OWNER_COLUMN_MALFORMED";

    // 三个兜底属性名以前在这里和 DataPermissionSpecification 各写一遍、靠注释约定"两处必须同值"。
    // §19.35 之后判据收进 SubjectDimensionRules，面板只能引用它 —— 提示语与真实兜底名同源，
    // 否则改名之后面板会继续建议一个已经不用的列名，而那看起来像"你登记错了"。
    static final String WAREHOUSE_ATTRIBUTE = SubjectDimensionRules.WAREHOUSE_ATTRIBUTE;

    static final String FACTORY_ATTRIBUTE = SubjectDimensionRules.FACTORY_ATTRIBUTE;

    static final String DEPARTMENT_DEFAULT_ATTRIBUTE = SubjectDimensionRules.DEPARTMENT_ATTRIBUTE;

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
     * @param shape         实体形状：标量属性、关联、关联目标的可绑列（由 {@link EntityShapeIndex#shapeOf}
     *                       给出）；attributes 为空表示没有映射
     * @param policyCounts  该资源上<b>启用中的</b>行级策略按主体类型的条数（决定"这档要不要报"）
     */
    public record Row(Long resourceId,
                      String resourceKey,
                      String tableName,
                      String tenantColumn,
                      String ownerColumns,
                      EntityShapeIndex.Shape shape,
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
            // 形状整体从 EntityShapeIndex 拿，不在这里用两个 Set 重建：重建一次就等于承认
            // "盘点看的和运行期用的可以不是同一份"，那正是本仓最贵的一类 bug
            EntityShapeIndex.Shape shape = row.shape() == null
                    ? new EntityShapeIndex.Shape(null, Set.of(), Set.of()) : row.shape();
            Map<String, Long> counts = row.policyCounts() == null ? Map.of() : row.policyCounts();
            if (shape.attributes().isEmpty()) {
                long total = counts.values().stream().mapToLong(Long::longValue).sum();
                risks.add(new Risk(row.resourceId(), row.resourceKey(), null, KIND_ENTITY_NOT_MAPPED,
                        "登记的表名「" + safe(row.tableName()) + "」没扫到对应实体类（@TableName）⇒ 该资源上 "
                                + total + " 条启用策略都没有列可绑：enforce 下落到「绑定失败」恒假（查不到数据），"
                                + "shadow 下只记审计。先确认表名是否写错、实体是否改了名，或该模块尚未接入。"));
                continue;
            }
            List<OwnerColumnRegistration> items = OwnerColumnRegistration.parse(row.ownerColumns());
            String malformed = OwnerColumnRegistration.validate(row.ownerColumns());
            if (malformed != null) {
                // 整串解析不出来时报这一条就够，后面的维度检查全部跳过（continue）：那些检查此时只能拿
                // 写死的默认列去猜，会把一个格式问题播成五条互相矛盾的说法，真正的原因反倒被埋掉
                risks.add(new Risk(row.resourceId(), row.resourceKey(), null, KIND_OWNER_COLUMN_MALFORMED,
                        "owner_columns 解析不出来：" + malformed + " ⇒ 这一串现在等于没登记，各档会一起退化到"
                                + "写死的默认列，看着配了其实没配。合法写法：creatorId、DEPARTMENT:departmentId、"
                                + "WAREHOUSE:storage.id（维度前缀可省，关联路径最多一跳）。"));
                continue;
            }
            List<String> untagged = OwnerColumnRegistration.untaggedPaths(items);

            check(risks, row, shape, DataPermissionSubjectType.TENANT.name(), counts, KIND_TENANT_UNBOUNDABLE,
                    "租户列", OwnerColumnRegistration.candidatesFor(items,
                            DataPermissionSubjectType.TENANT.name(), csv(row.tenantColumn())), true);
            check(risks, row, shape, DataPermissionSubjectType.USER.name(), counts, KIND_OWNER_UNBOUNDABLE,
                    "负责人列",
                    OwnerColumnRegistration.candidatesFor(items, DataPermissionSubjectType.USER.name(), untagged),
                    false);
            department(risks, row, shape, counts, items, untagged);
            check(risks, row, shape, DataPermissionSubjectType.WAREHOUSE.name(), counts, KIND_WAREHOUSE_UNBOUNDABLE,
                    "仓库列（缺省按写死的 warehouseId 找，登记 WAREHOUSE:xxx 可改绑）",
                    OwnerColumnRegistration.candidatesFor(items, DataPermissionSubjectType.WAREHOUSE.name(),
                            List.of(WAREHOUSE_ATTRIBUTE)), false);
            check(risks, row, shape, DataPermissionSubjectType.FACTORY.name(), counts, KIND_FACTORY_UNBOUNDABLE,
                    "工厂列（缺省按写死的 factoryId 找，登记 FACTORY:xxx 可改绑）",
                    OwnerColumnRegistration.candidatesFor(items, DataPermissionSubjectType.FACTORY.name(),
                            List.of(FACTORY_ATTRIBUTE)), false);
        }
        return risks;
    }

    /**
     * 部门档单独处理：它读的是 owner_columns 的第一项，因此可能与负责人档抢同一列
     */
    private static void department(List<Risk> risks,
                                   Row row,
                                   EntityShapeIndex.Shape shape,
                                   Map<String, Long> counts,
                                   List<OwnerColumnRegistration> items,
                                   List<String> untagged) {
        long using = count(counts, DataPermissionSubjectType.DEPARTMENT.name());
        if (using <= 0) {
            return;
        }
        List<String> explicit = OwnerColumnRegistration.pathsFor(items, DataPermissionSubjectType.DEPARTMENT.name());
        // 「抢同一列」只在部门没有自己的登记时才成立：一旦写了 DEPARTMENT:xxx，两档各看各的列，
        // 继续报就是纯粹骚扰运营
        if (explicit.isEmpty() && untagged.size() == 1
                && count(counts, DataPermissionSubjectType.USER.name()) > 0) {
            risks.add(new Risk(row.resourceId(), row.resourceKey(), DataPermissionSubjectType.DEPARTMENT.name(),
                    KIND_DEPARTMENT_COLUMN_SHARED,
                    "owner_columns 只登记了「" + untagged.get(0) + "」一项，而它同时被两档使用：负责人策略拿它比用户 ID、"
                            + "部门策略拿它比部门 ID ⇒ 两档里必有一档比出错值（绑得上，所以也不会进绑定失败分支，"
                            + "连审计都不记）。就地解法：给部门单独立一项登记，写成 "
                            + DataPermissionSubjectType.DEPARTMENT.name() + OwnerColumnRegistration.SEPARATOR
                            + DEPARTMENT_DEFAULT_ATTRIBUTE + "（列名或关联路径都行），负责人档继续用裸写法；"
                            + "或别再对这类资源配 DEPARTMENT 策略。"));
            return;
        }
        List<String> columns = explicit.isEmpty()
                ? (untagged.isEmpty() ? List.of(DEPARTMENT_DEFAULT_ATTRIBUTE) : List.of(untagged.get(0)))
                : explicit;
        check(risks, row, shape, DataPermissionSubjectType.DEPARTMENT.name(), counts, KIND_DEPARTMENT_UNBOUNDABLE,
                explicit.isEmpty() && untagged.isEmpty()
                        ? "部门列（未登记 owner_columns 时按 departmentId 找）" : "部门列", columns, false);
    }

    /**
     * 一档判定的公共形状：没登记与"登记了但取不到"分开说，前者是漏配、后者是配错或形状不对
     */
    private static void check(List<Risk> risks, Row row, EntityShapeIndex.Shape shape, String subjectType,
                              Map<String, Long> counts, String kind, String what, List<String> names,
                              boolean missingIsConfigError) {
        long using = count(counts, subjectType);
        if (using <= 0) {
            return;
        }
        if (names == null) {
            names = List.of();
        }
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
            // 关联路径已经能走了（一跳封顶，见 OwnerColumnRegistration#MAX_SEGMENTS），所以这里给的是能直接抄进登记框的写法，
            // 而不是"等内核补能力"——后者会把一个已经能解决的问题说成待建设项
            detail.append(" 注意 ").append(association).append(" 在实体上是关联而不是标量列：不是名字写错，是登记方式要换 —— ")
                    .append("写成 ").append(subjectType).append(OwnerColumnRegistration.SEPARATOR).append(association)
                    .append(".id（沿关联走一跳即可，见施工图 D1(b)）。");
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
     * 逗号分隔的登记值 → 逐项（与 {@code SubjectDimensionRules} 取候选列的方式一致）
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
