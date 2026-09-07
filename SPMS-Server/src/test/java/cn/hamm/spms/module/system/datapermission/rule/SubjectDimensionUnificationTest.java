package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>归属判据合一的契约用例（§19.35）</h1>
 *
 * <p>这一格没有新增功能，它做的是"把一句话从两处收到一处"。要保证的不是"新逻辑对"，
 * 而是<b>三个消费方读到的是同一棵树、并且各自的分歧都是刻意的</b>：</p>
 * <ul>
 *   <li>{@link JpaPermissionRuleCompiler}：候选列按元模型试绑，绑不上的跳过；</li>
 *   <li>{@link SqlPermissionRuleRenderer}：没有元模型可问，按登记顺序取，绑不上就是 SQL 报错；</li>
 *   <li>{@link PermissionRuleEvaluator}：语义裁判（行样本 + 三值逻辑保守子集），
 *       前两个渲染器任何一边跑偏，这里就会给出不同的答案。</li>
 * </ul>
 *
 * <p>所以每条用例都是"给同一棵树，问三个方向"，而不是各测一遍各自的实现 —— 各测一遍
 * 恰恰是合一之前那种"两份实现各自绿"的形状。</p>
 */
@DisplayName("归属判据合一：一棵树，三个渲染方向")
class SubjectDimensionUnificationTest {

    private static final String RESOURCE = "asset.material";

    private final SqlPermissionRuleRenderer renderer = new SqlPermissionRuleRenderer();
    private final PermissionRuleEvaluator evaluator = new PermissionRuleEvaluator();

    private DataResourceEntity resource(String tenantColumn, String ownerColumns) {
        DataResourceEntity resource = new DataResourceEntity();
        resource.setResourceKey(RESOURCE);
        resource.setTableName("material");
        resource.setTenantColumn(tenantColumn);
        resource.setOwnerColumns(ownerColumns);
        return resource;
    }

    private DataPolicyEntity policy(String subjectType, Long subjectId) {
        DataPolicyEntity policy = new DataPolicyEntity();
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId);
        return policy;
    }

    private PermissionContext context(Long userId, Long tenantId, Set<Long> departments,
                                      Set<Long> factories, Set<Long> warehouses) {
        return new PermissionContext(userId, tenantId, departments, departments, factories, warehouses,
                Set.of(), false);
    }

    @Nested
    @DisplayName("树形状：主体类型 → 候选列与组合方式")
    class TreeShape {

        @Test
        @DisplayName("租户档：候选来自 tenant_column，只取第一个绑得上的")
        void tenantDimension() {
            SubjectDimensionRules.SubjectRule rule = SubjectDimensionRules.forPolicy(
                    resource("tenant_id", null), policy("TENANT", null), context(1L, 100L, Set.of(), Set.of(), Set.of()));

            PermissionRule.Dimension dimension = assertInstanceOf(PermissionRule.Dimension.class, rule.rule());
            assertEquals(List.of("tenant_id"), dimension.candidateFields());
            assertEquals(100L, dimension.value());
            assertFalse(dimension.orAllCandidates(), "租户档是「挑第一个能绑上的」，多列 OR 是负责人档的语义");
            assertTrue(rule.shouldBind());
        }

        @Test
        @DisplayName("负责人档：裸写法全部算我的列，且是 OR")
        void ownerDimensionOrsEveryCandidate() {
            SubjectDimensionRules.SubjectRule rule = SubjectDimensionRules.forPolicy(
                    resource("tenant_id", "creator_id,owner_id"), policy("USER", null),
                    context(7L, 100L, Set.of(), Set.of(), Set.of()));

            PermissionRule.Dimension dimension = assertInstanceOf(PermissionRule.Dimension.class, rule.rule());
            assertEquals(List.of("creator_id", "owner_id"), dimension.candidateFields());
            assertTrue(dimension.orAllCandidates());
            assertEquals(7L, dimension.value());
        }

        @Test
        @DisplayName("ROLE / SYSTEM 不参与行级过滤：连「本该绑」都不记，否则面板刷假红灯")
        void nonFilteringSubjectsDoNotCount() {
            PermissionContext context = context(1L, 100L, Set.of(), Set.of(), Set.of());
            for (String type : List.of("ROLE", "SYSTEM", "UNKNOWN_KIND")) {
                SubjectDimensionRules.SubjectRule rule =
                        SubjectDimensionRules.forPolicy(resource("tenant_id", null), policy(type, null), context);
                assertInstanceOf(PermissionRule.AllowAll.class, rule.rule(), type);
                assertFalse(rule.shouldBind(), type + " 被算成绑定失败 = 结构性空转指标里混进设计如此");
            }
        }

        @Test
        @DisplayName("部门档：主体范围不含这条策略时不拼条件，但仍计入绑定失败（与合一前逐字同形）")
        void departmentGateKeepsTheOldAccounting() {
            DataResourceEntity resource = resource("tenant_id", "DEPARTMENT:dept_id");
            SubjectDimensionRules.SubjectRule outside = SubjectDimensionRules.forPolicy(
                    resource, policy("DEPARTMENT", 42L), context(1L, 100L, Set.of(7L), Set.of(), Set.of()));
            assertInstanceOf(PermissionRule.AllowAll.class, outside.rule());
            assertTrue(outside.shouldBind());

            SubjectDimensionRules.SubjectRule inside = SubjectDimensionRules.forPolicy(
                    resource, policy("DEPARTMENT", 7L), context(1L, 100L, Set.of(7L), Set.of(), Set.of()));
            assertEquals(List.of("dept_id"),
                    assertInstanceOf(PermissionRule.Dimension.class, inside.rule()).candidateFields());
        }

        @Test
        @DisplayName("工厂 / 仓库档：值取自策略主体，兜底名与盘点面板同源")
        void factoryAndWarehouseUseRegisteredNames() {
            PermissionContext context = context(1L, 100L, Set.of(), Set.of(9L), Set.of(8L));
            PermissionRule.Dimension factory = assertInstanceOf(PermissionRule.Dimension.class,
                    SubjectDimensionRules.forPolicy(resource("tenant_id", null), policy("FACTORY", 9L), context).rule());
            assertEquals(List.of(SubjectDimensionRules.FACTORY_ATTRIBUTE), factory.candidateFields());
            assertEquals(9L, factory.value());

            PermissionRule.Dimension warehouse = assertInstanceOf(PermissionRule.Dimension.class,
                    SubjectDimensionRules.forPolicy(resource("tenant_id", null), policy("WAREHOUSE", 8L), context).rule());
            assertEquals(List.of(SubjectDimensionRules.WAREHOUSE_ATTRIBUTE), warehouse.candidateFields());
        }
    }

    @Nested
    @DisplayName("三向一致：同一棵树的 JPA / SQL / 内存裁判")
    class ThreeWayParity {

        private String sql(PermissionRule rule, String alias, PermissionContext context) {
            RowPermissionPredicate rendered = renderer.render(rule, alias, new PermissionRuleContext(context));
            return rendered == null ? null : rendered.sql();
        }

        @Test
        @DisplayName("命中与不命中：三个方向给同一个答案")
        void matchAndDismatchAgree() {
            SubjectDimensionRules.SubjectRule subject = SubjectDimensionRules.forPolicy(
                    resource("tenant_id", null), policy("TENANT", null), context(1L, 100L, Set.of(), Set.of(), Set.of()));

            assertEquals("(material.tenant_id = :dpRule0)", sql(subject.rule(), "material", context(1L, 100L, Set.of(), Set.of(), Set.of())));
            assertTrue(evaluator.matches(subject.rule(), Map.of("tenant_id", 100L), null));
            assertFalse(evaluator.matches(subject.rule(), Map.of("tenant_id", 101L), null));
        }

        @Test
        @DisplayName("OR 语义：两个候选列都能绑上时，SQL 侧也保留两个分支")
        void orSemanticsAreNotSilentlyTruncated() {
            SubjectDimensionRules.SubjectRule subject = SubjectDimensionRules.forPolicy(
                    resource("tenant_id", "creator_id,owner_id"), policy("USER", null),
                    context(7L, 100L, Set.of(), Set.of(), Set.of()));

            assertEquals("(material.creator_id = :dpRule0 OR material.owner_id = :dpRule1)",
                    sql(subject.rule(), "material", context(7L, 100L, Set.of(), Set.of(), Set.of())));
            // 裁判侧：任一列属于我 ⇒ 可见（不是"两列都要等于我"）
            assertTrue(evaluator.matches(subject.rule(), Map.of("creator_id", 1L, "owner_id", 7L), null));
            assertFalse(evaluator.matches(subject.rule(), Map.of("creator_id", 1L, "owner_id", 2L), null));
        }

        @Test
        @DisplayName("这一维取不到值 ⇒ 恒假，且恒假只有一处写法")
        void missingContextValueDenies() {
            SubjectDimensionRules.SubjectRule subject = SubjectDimensionRules.forPolicy(
                    resource("tenant_id", null), policy("TENANT", null), context(1L, null, Set.of(), Set.of(), Set.of()));

            // 渲染器给每个片段都套一层括号（appendGroup），恒假也不例外：断言写的是它的真实输出，
            // 不是"我以为的常量值"——上一条用例就红在这个括号上
            assertEquals('(' + RowPermissionPredicate.DENY_SQL + ')',
                    sql(subject.rule(), "material", context(1L, null, Set.of(), Set.of(), Set.of())));
            assertFalse(evaluator.matches(subject.rule(), Map.of("tenant_id", 1L), null));
            // 列在、值为 null（归属没落库的存量行）：裁判必须判"不成立"，
            // 否则"恒假"只覆盖了配置缺失，没覆盖数据缺失
            Map<String, Object> rowWithNullOwner = new java.util.HashMap<>();
            rowWithNullOwner.put("tenant_id", null);
            assertFalse(evaluator.matches(subject.rule(), rowWithNullOwner, null));
        }

        @Test
        @DisplayName("候选列写成关联路径：SQL 侧明确拒绝，而不是猜一个列名")
        void associationPathIsRefusedOnSqlSide() {
            SubjectDimensionRules.SubjectRule subject = SubjectDimensionRules.forPolicy(
                    resource("tenant_id", "WAREHOUSE:storage.id"), policy("WAREHOUSE", 8L),
                    context(1L, 100L, Set.of(), Set.of(), Set.of(8L)));

            // JPA 侧沿一跳能绑上（D1(b) 已支持），SQL 侧要显式 JOIN ⇒ 只能拒；
            // 猜成 storage_id 的后果是"命中一个同名别的列"，那是静默放宽范围，查不出来。
            PermissionRuleException ex = assertThrows(PermissionRuleException.class,
                    () -> sql(subject.rule(), "material", context(1L, 100L, Set.of(), Set.of(), Set.of(8L))));
            assertTrue(ex.getMessage().contains("D1(b)"), ex.getMessage());
        }

        @Test
        @DisplayName("不合法的候选列名在 SQL 侧被标识符白名单挡掉（不落到字符串拼接）")
        void unsafeCandidateIsRejected() {
            SubjectDimensionRules.SubjectRule subject = new SubjectDimensionRules.SubjectRule(
                    new PermissionRule.Dimension(PermissionRuleOp.EQ, List.of("tenant;drop"), 1L, false), true);

            assertThrows(PermissionRuleException.class,
                    () -> sql(subject.rule(), "material", context(1L, 100L, Set.of(), Set.of(), Set.of())));
        }
    }
}
