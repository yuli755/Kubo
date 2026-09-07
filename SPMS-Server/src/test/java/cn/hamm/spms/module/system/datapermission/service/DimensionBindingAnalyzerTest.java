package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>维度可绑定性判定的用例</h1>
 *
 * <p>每条断言都对应 {@code DataPermissionSpecification} 里一个真实的"取不到就返回 null"分支：
 * 那类分支的代价不是报错，而是这一档策略在 enforce 下变成恒假、在 shadow 下变成不存在。
 * 因此这里既测"该报的要报"，也测<b>"不该报的别报"</b> —— 面板多喊一次，之后就没人看它了。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DimensionBindingAnalyzer · 这一档有没有列可绑")
class DimensionBindingAnalyzerTest {

    private static DimensionBindingAnalyzer.Row row(String tenantColumn,
                                                    String ownerColumns,
                                                    Set<String> attributes,
                                                    Set<String> associations,
                                                    Map<String, Long> counts) {
        return new DimensionBindingAnalyzer.Row(1L, "wms.inventory", "inventory",
                tenantColumn, ownerColumns, attributes, associations, counts);
    }

    private static List<DimensionBindingAnalyzer.Risk> risks(DimensionBindingAnalyzer.Row row) {
        return DimensionBindingAnalyzer.risks(List.of(row));
    }

    private static DimensionBindingAnalyzer.Risk onlyOne(List<DimensionBindingAnalyzer.Risk> found, String kind) {
        assertEquals(1, found.size(), "期望恰好一条 " + kind + "，实际：" + found);
        assertEquals(kind, found.get(0).kind());
        return found.get(0);
    }

    @Test
    @DisplayName("表名在元模型里找不到 → 只报一条「实体未映射」，不逐维度刷屏")
    void unmappedEntityReportsOnce() {
        List<DimensionBindingAnalyzer.Risk> found = risks(
                row("tenant_id", "creatorId", Set.of(), Set.of(), Map.of("TENANT", 2L, "USER", 1L)));

        DimensionBindingAnalyzer.Risk risk = onlyOne(found, DimensionBindingAnalyzer.KIND_ENTITY_NOT_MAPPED);
        assertNull(risk.subjectType(), "整表没映射时不属于任何一档维度");
        assertTrue(risk.detail().contains("3 条启用策略"), risk.detail());
    }

    @Test
    @DisplayName("登记列名 tenant_id、实体上只有 tenantId → 不报（与运行期同一套候选写法）")
    void columnStyleRegistrationIsBindable() {
        assertTrue(risks(row("tenant_id", null, Set.of("tenantId"), Set.of(), Map.of("TENANT", 1L))).isEmpty());
    }

    @Test
    @DisplayName("租户列在实体上不存在 → 报出来，并写清有几条策略受影响")
    void missingTenantColumnIsReported() {
        List<DimensionBindingAnalyzer.Risk> found = risks(
                row("tenant_id", null, Set.of("id", "name"), Set.of(), Map.of("TENANT", 3L)));

        DimensionBindingAnalyzer.Risk risk = onlyOne(found, DimensionBindingAnalyzer.KIND_TENANT_UNBOUNDABLE);
        assertEquals("TENANT", risk.subjectType());
        assertTrue(risk.detail().contains("tenant_id"), risk.detail());
        assertTrue(risk.detail().contains("3"), risk.detail());
    }

    @Test
    @DisplayName("租户列没登记 → 单独一句话：漏配与配错不是同一个修法")
    void unregisteredTenantColumnSaysSo() {
        List<DimensionBindingAnalyzer.Risk> found = risks(
                row(null, null, Set.of("tenantId"), Set.of(), Map.of("TENANT", 1L)));

        DimensionBindingAnalyzer.Risk risk = onlyOne(found, DimensionBindingAnalyzer.KIND_TENANT_UNBOUNDABLE);
        assertTrue(risk.detail().contains("没有登记"), risk.detail());
        assertTrue(risk.detail().contains("tenantId"), risk.detail());
    }

    @Test
    @DisplayName("owner_columns 是逗号列表：任一项能绑就算能绑（照抄整串会假报）")
    void ownerColumnsAreMatchedPerItem() {
        assertTrue(risks(row(null, "creatorId,storageId", Set.of("storageId"), Set.of(),
                Map.of("USER", 2L))).isEmpty());
        List<DimensionBindingAnalyzer.Risk> none = risks(row(null, "creatorId", Set.of("id"), Set.of(),
                Map.of("USER", 1L)));
        onlyOne(none, DimensionBindingAnalyzer.KIND_OWNER_UNBOUNDABLE);
    }

    @Test
    @DisplayName("仓库列绑不上但实体有 storage 关联 → 说的是缺关联路径，不是说你名字写错")
    void warehouseOnAssociationIsPhrasedDifferently() {
        List<DimensionBindingAnalyzer.Risk> found = risks(
                row(null, null, Set.of("id"), Set.of("storage"), Map.of("WAREHOUSE", 1L)));

        DimensionBindingAnalyzer.Risk risk = onlyOne(found, DimensionBindingAnalyzer.KIND_WAREHOUSE_UNBOUNDABLE);
        assertTrue(risk.detail().contains("storage"), risk.detail());
        assertTrue(risk.detail().contains("D1(b)"), risk.detail());
    }

    @Test
    @DisplayName("部门列取的就是 owner_columns 第一项 → 与负责人列抢同一列时报「语义共用」")
    void departmentSharingOwnerColumnIsCalledOut() {
        List<DimensionBindingAnalyzer.Risk> found = risks(
                row(null, "creatorId", Set.of("creatorId"), Set.of(), Map.of("USER", 1L, "DEPARTMENT", 2L)));

        DimensionBindingAnalyzer.Risk risk = onlyOne(found, DimensionBindingAnalyzer.KIND_DEPARTMENT_COLUMN_SHARED);
        assertTrue(risk.detail().contains("两档"), risk.detail());
    }

    @Test
    @DisplayName("没登记 owner_columns 时按 departmentId 找：找不到就报出这个名字")
    void departmentFallsBackToDefaultAttributeName() {
        List<DimensionBindingAnalyzer.Risk> found = risks(
                row(null, null, Set.of("id"), Set.of(), Map.of("DEPARTMENT", 1L)));

        DimensionBindingAnalyzer.Risk risk = onlyOne(found, DimensionBindingAnalyzer.KIND_DEPARTMENT_UNBOUNDABLE);
        assertTrue(risk.detail().contains("departmentId"), risk.detail());
    }

    @Test
    @DisplayName("这一档没有启用策略 → 一个字都不说（噪音会让面板失去可信度）")
    void dimensionsWithoutPoliciesStaySilent() {
        assertTrue(risks(row("tenant_id", "creator_id", Set.of("id"), Set.of(), Map.of())).isEmpty());
    }

    @Test
    @DisplayName("空入参与 null 行 → 空结果，不抛异常")
    void inputsAreNullSafe() {
        assertTrue(DimensionBindingAnalyzer.risks(null).isEmpty());
        assertTrue(DimensionBindingAnalyzer.risks(List.of()).isEmpty());
        assertTrue(DimensionBindingAnalyzer.risks(Arrays.asList((DimensionBindingAnalyzer.Row) null)).isEmpty());
    }

    @Test
    @DisplayName("按主体类型计数：忽略空白与 null，大小写原样保留")
    void countBySubjectTypeIsTolerant() {
        Map<String, Long> counts = DimensionBindingAnalyzer.countBySubjectType(
                Arrays.asList("TENANT", " tenant ", null, "  ", "TENANT"));

        assertEquals(2, counts.size());
        assertEquals(2L, counts.get("TENANT"));
        assertEquals(1L, counts.get("tenant"));
    }
}
