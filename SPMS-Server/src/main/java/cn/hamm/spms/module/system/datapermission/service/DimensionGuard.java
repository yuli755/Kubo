package cn.hamm.spms.module.system.datapermission.service;

/**
 * <h1>尚未落地维度的写入侧拦截</h1>
 *
 * <p>工厂（FACTORY）维度在数据模型上是<b>恒空</b>的：没有 {@code user_factory} 关系表，
 * {@code PermissionContext.factoryIds()} 永远是空集合。于是：</p>
 * <ul>
 *   <li>{@code DataPermissionService} / {@code JpaPermissionRuleProvider} 的主体匹配
 *       写成 {@code context.factoryIds().contains(subjectId)} —— 空集合里永远不含任何 id，
 *       <b>任何人配的 FACTORY 策略对任何人都不会命中</b>；</li>
 *   <li>若某资源只配了 FACTORY 策略，读链路的结果是"等价于没配策略"（除非资源开了默认拒绝）
 *       —— 管理员看到"保存成功"，实际防护为零；</li>
 *   <li>反过来，{@code condition_json} 里引用 {@code factoryIds} 时
 *       {@code RowPermissionPredicate.factory(...)} 对空集合返回 {@code deny()}
 *       —— 同一个恒空字段，在另一条链路上是<b>全员空结果</b>的自锁。</li>
 * </ul>
 *
 * <p>两端都不是"没生效"这么轻：<b>要么假安全，要么集体看不到数据</b>。
 * 因此在建表（施工图 D13）之前，写入侧直接拒绝，而不是让它静默躺在策略表里。
 * 这里刻意做成无状态静态判断，便于单测与将来放开（建表后把 available 改成 true 即可）。</p>
 *
 * @author Hamm.cn
 */
public final class DimensionGuard {

    /**
     * 工厂维度是否可用
     *
     * <p>建 {@code user_factory} 关系表并在 {@code PermissionContext} 里真实解析后，
     * 这里改成 true（连同 DataPermissionService 侧的匹配逻辑一并生效），
     * 同时删掉本类的拒绝分支即可。</p>
     */
    public static final boolean FACTORY_AVAILABLE = false;

    private static final String SUBJECT_FACTORY = "FACTORY";
    private static final String FACTORY_IDS = "factoryIds";

    private DimensionGuard() {
    }

    /**
     * 主体类型是否引用了尚未落地的维度
     *
     * @param subjectType 策略主体类型（TENANT / USER / DEPARTMENT / FACTORY / WAREHOUSE / ROLE / SYSTEM）
     * @return 拒绝原因；{@code null} 表示允许
     */
    public static String rejectUnavailableSubject(String subjectType) {
        if (!FACTORY_AVAILABLE && SUBJECT_FACTORY.equalsIgnoreCase(subjectType)) {
            return "工厂（FACTORY）维度尚未落地：没有 user_factory 关系表，factoryIds 恒为空，"
                    + "该策略对任何人都不会命中（假安全），或在条件树里命中 deny() 造成全员空结果。"
                    + "请改用 DEPARTMENT / TENANT 维度，或先按施工图 D13 建表";
        }
        return null;
    }

    /**
     * 条件树里是否引用了尚未落地的变量
     *
     * @param conditionJson 策略条件（可为空）
     * @return 拒绝原因；{@code null} 表示允许
     */
    public static String rejectUnavailableCondition(String conditionJson) {
        if (FACTORY_AVAILABLE || conditionJson == null || conditionJson.isBlank()) {
            return null;
        }
        if (conditionJson.contains(FACTORY_IDS)) {
            return "条件树引用了 factoryIds，但工厂维度尚未落地：该变量恒为空集合，"
                    + "会被渲染成恒假条件（整个列表返回空）。请改用 departmentIds / tenantId";
        }
        return null;
    }
}
