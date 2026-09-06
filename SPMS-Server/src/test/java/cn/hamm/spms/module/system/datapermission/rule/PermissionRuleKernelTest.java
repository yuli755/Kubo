package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/**
 * <h1>条件树内核测试（P1 Gate：禁止裸 SQL + 3 层嵌套 + 两个实现语义一致）</h1>
 *
 * <p>这组用例是"能不能把行级过滤交给配置"的门槛，不是覆盖率道具：</p>
 * <ul>
 *   <li>解析/校验：任何能塞进 SQL 文本的形状都必须被拒；空数组 {@code IN} 必须被拒；</li>
 *   <li>列名/属性名互通：否则又会回到"策略配了、条件生成不出来"；</li>
 *   <li>渲染器与编译器在同一棵树上给出相同结构（参数个数、恒假位置、别名前缀）；</li>
 *   <li>求值器作为基准覆盖 3 层嵌套的真值表。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class PermissionRuleKernelTest {

    private static final String RESOURCE = "wms.inventory";

    private PermissionRuleParser parser;
    private PermissionRuleContext context;

    @Mock
    private Root<?> root;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<Object> path;

    @Mock
    private Predicate predicate;

    @BeforeEach
    void setUp() {
        parser = new PermissionRuleParser();
        context = new PermissionRuleContext(new PermissionContext(
                7L, 100L, Set.of(10L), Set.of(), Set.of(1L, 2L), Set.of("planner"), false));
        lenient().when(builder.disjunction()).thenReturn(predicate);
        lenient().when(path.getJavaType()).thenReturn((Class) Long.class);
    }

    private PermissionRuleValidator.FieldAllowList allowList(String... columns) {
        return PermissionRuleValidator.FieldAllowList.of(columns);
    }

    @Nested
    @DisplayName("解析：只接受受控形状")
    class Parsing {

        @Test
        @DisplayName("空配置解析成\"无附加条件\"，保证存量 {} 不被打成 DENY")
        void emptyJsonMeansAllowAll() {
            assertInstanceOf(PermissionRule.AllowAll.class, parser.parse(RESOURCE, "{}"));
            assertInstanceOf(PermissionRule.AllowAll.class, parser.parse(RESOURCE, null));
            assertInstanceOf(PermissionRule.AllowAll.class, parser.parse(RESOURCE, "  "));
            assertFalse(parser.parse(RESOURCE, "{}").contributes(), "AllowAll 不应产生条件");
        }

        @Test
        @DisplayName("塞 SQL 片段一律拒绝，并给出可运维的原因")
        void rejectsRawSql() {
            for (String json : List.of(
                    "{\"sql\":\"1=1\"}",
                    "{\"where\":\"tenant_id = 1\"}",
                    "{\"expression\":\"1 OR 1\"}",
                    "{\"rawSql\":\"...\"}")) {
                PermissionRuleException ex = assertThrows(PermissionRuleException.class, () -> parser.parse(RESOURCE, json),
                        "应当拒绝：" + json);
            assertTrue(ex.getMessage().contains("禁止裸 SQL") || ex.getMessage().contains("未知键"),
                    "报错要点明是哪种非法形状，实际：" + ex.getMessage());
            }
        }

        /**
         * 与"禁止裸 SQL"对称的另一半：**值**不是标识符，它永远走参数绑定，
         * 因此值里出现 SQL 片段不构成注入面。把这条写成断言，是为了防止后来者
         * 误加"关键词黑名单"（那种做法既拦不住攻击又会误杀合法业务数据）。
         */
        @Test
        @DisplayName("值里的 SQL 文本被当数据处理：绑定为参数，不进 SQL 结构")
        void valueIsBoundNotConcatenated() {
            String payload = "{\"field\":\"remark\",\"op\":\"EQ\",\"value\":\"1; DROP TABLE x\"}";
            PermissionRule rule = parser.parse(RESOURCE, payload);
            RowPermissionPredicate rendered = new SqlPermissionRuleRenderer()
                    .render(rule, null, context);
            assertEquals("(remark = :dpRule0)", rendered.sql());
            assertEquals("1; DROP TABLE x", rendered.parameters().get("dpRule0"));
            assertFalse(rendered.sql().contains("DROP"), "SQL 文本里不应出现值");
        }

        @Test
        @DisplayName("deny/allow 是显式表达，且不允许与别的键混写")
        void explicitDenyAndAllow() {
            assertInstanceOf(PermissionRule.Deny.class, parser.parse(RESOURCE, "{\"deny\":true}"));
            assertInstanceOf(PermissionRule.AllowAll.class, parser.parse(RESOURCE, "{\"allow\":true}"));
            assertThrows(PermissionRuleException.class, () -> parser.parse(RESOURCE, "{\"deny\":true,\"field\":\"a\"}"));
        }

        @Test
        @DisplayName("叶子缺 value 与 contextVar 时报错，而不是退化成恒真")
        void leafNeedsSomethingToCompare() {
            PermissionRuleException ex = assertThrows(PermissionRuleException.class,
                    () -> parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"op\":\"EQ\"}"));
            assertTrue(ex.getMessage().contains("value"), ex.getMessage());
        }

        @Test
        @DisplayName("缺 op 的叶子按 EQ 处理（便于手写简单条件）")
        void leafDefaultsToEq() {
            PermissionRule rule = parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"value\":5}");
            PermissionRule.Leaf leaf = assertInstanceOf(PermissionRule.Leaf.class, rule);
            assertEquals(PermissionRuleOp.EQ, leaf.op());
            assertEquals(5L, leaf.value(), "整数一律解析为 Long，后续按路径类型收敛");
        }

        @Test
        @DisplayName("超过 8 层直接拒绝：套娃不是表达力，是排障地狱")
        void rejectsTooDeepTrees() {
            StringBuilder json = new StringBuilder();
            int depth = PermissionRuleValidator.MAX_DEPTH + 2;
            for (int i = 0; i < depth; i++) {
                json.append("{\"op\":\"ALL_OF\",\"children\":[");
            }
            json.append("{\"field\":\"tenantId\",\"value\":1}");
            for (int i = 0; i < depth; i++) {
                json.append("]}");
            }
            String payload = json.toString();
            assertTrue(payload.length() > 10);
            assertThrows(PermissionRuleException.class, () -> parser.parse(RESOURCE, payload));
        }

        @Test
        @DisplayName("非法 JSON 与\"根不是对象\"分别报出不同原因")
        void reportsParseErrors() {
            assertTrue(assertThrows(PermissionRuleException.class,
                    () -> parser.parse(RESOURCE, "{\"field\":")).getMessage().contains("合法 JSON"));
            assertTrue(assertThrows(PermissionRuleException.class,
                    () -> parser.parse(RESOURCE, "[1,2]")).getMessage().contains("必须是对象"));
        }
    }

    @Nested
    @DisplayName("校验：列白名单与形态")
    class Validation {

        @Test
        @DisplayName("不在白名单的列一律拒绝（白名单为空 = 全部拒绝，不猜列名）")
        void fieldMustBeAllowListed() {
            PermissionRule rule = parser.parse(RESOURCE, "{\"field\":\"secretColumn\",\"value\":1}");
            PermissionRuleException ex = assertThrows(PermissionRuleException.class,
                    () -> new PermissionRuleValidator(allowList()).validate(RESOURCE, rule));
            assertTrue(ex.getMessage().contains("白名单"), ex.getMessage());
        }

        @Test
        @DisplayName("登记 DB 列名后，策略里写 camelCase 也认（两侧命名互通）")
        void snakeAndCamelAreInterchangeable() {
            PermissionRule rule = parser.parse(RESOURCE, "{\"field\":\"warehouseId\",\"value\":1}");
            new PermissionRuleValidator(allowList("warehouse_id")).validate(RESOURCE, rule);
        }

        @Test
        @DisplayName("空数组 IN 属于配置错误：要么显式 deny，要么别写")
        void emptyInArrayIsRejected() {
            PermissionRule rule = parser.parse(RESOURCE, "{\"field\":\"warehouseId\",\"op\":\"IN\",\"value\":[]}");
            PermissionRuleException ex = assertThrows(PermissionRuleException.class,
                    () -> new PermissionRuleValidator(allowList("warehouseId")).validate(RESOURCE, rule));
            assertTrue(ex.getMessage().contains("deny"), ex.getMessage());
        }

        @Test
        @DisplayName("算子与值形态必须匹配")
        void opValueShapeMustMatch() {
            PermissionRuleValidator validator = new PermissionRuleValidator(allowList("remark", "tenantId"));
            assertThrows(PermissionRuleException.class, () -> validator.validate(RESOURCE,
                    parser.parse(RESOURCE, "{\"field\":\"remark\",\"op\":\"LIKE\",\"value\":123}")));
            assertThrows(PermissionRuleException.class, () -> validator.validate(RESOURCE,
                    parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"op\":\"IS_NULL\",\"value\":1}")));
            assertThrows(PermissionRuleException.class, () -> validator.validate(RESOURCE,
                    parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"op\":\"IN\",\"contextVar\":\"tenantId\"}")));
            assertThrows(PermissionRuleException.class, () -> validator.validate(RESOURCE,
                    parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"op\":\"EQ\",\"contextVar\":\"notAVar\"}")));
        }

        @Test
        @DisplayName("分组子节点数与叶子总数有上限")
        void capsGroupAndLeafCount() {
            List<String> tooMany = new ArrayList<>();
            for (int i = 0; i <= PermissionRuleValidator.MAX_CHILDREN; i++) {
                tooMany.add("{\"field\":\"tenantId\",\"value\":1}");
            }
            PermissionRule rule = parser.parse(RESOURCE,
                    "{\"op\":\"ALL_OF\",\"children\":[" + String.join(",", tooMany) + "]}");
            assertThrows(PermissionRuleException.class,
                    () -> new PermissionRuleValidator(allowList("tenantId")).validate(RESOURCE, rule));
        }
    }

    @Nested
    @DisplayName("渲染器（P2 SQL 侧）与编译器（当前 JPA 侧）语义一致")
    class BothImplementationsAgree {

        private final SqlPermissionRuleRenderer renderer = new SqlPermissionRuleRenderer();
        private final JpaPermissionRuleCompiler compiler = new JpaPermissionRuleCompiler();

        @Test
        @DisplayName("上下文标量 → 列 = :参数，别名与 snake_case 都由渲染器负责")
        void rendersContextScalar() {
            PermissionRule rule = parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"contextVar\":\"tenantId\"}");
            RowPermissionPredicate rendered = renderer.render(rule, null, context);
            assertEquals("(tenant_id = :dpRule0)", rendered.sql());
            assertEquals(100L, rendered.parameters().get("dpRule0"));
        }

        @Test
        @DisplayName("表别名会被同样校验，非法别名不拼进 SQL")
        void validatesAliasIdentifier() {
            PermissionRule rule = parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"value\":1}");
            assertThrows(PermissionRuleException.class, () -> renderer.render(rule, "inv; DROP TABLE x", context));
            assertEquals("(inv.tenant_id = :dpRule0)", renderer.render(rule, "inv", context).sql());
        }

        @Test
        @DisplayName("集合走逐项占位符，绝不把值拼进文本")
        void rendersInAsParameterList() {
            PermissionRule rule = parser.parse(RESOURCE,
                    "{\"field\":\"warehouseId\",\"op\":\"IN\",\"contextVar\":\"warehouseIds\"}");
            RowPermissionPredicate rendered = renderer.render(rule, "inv", context);
            assertEquals("(inv.warehouse_id IN (:dpRule0_0, :dpRule0_1))", rendered.sql());
            assertEquals(2, rendered.parameters().size());
            assertTrue(rendered.parameters().containsValue(1L));
            assertTrue(rendered.parameters().containsValue(2L));
        }

        @Test
        @DisplayName("主体没有任何仓库 → 恒假而不是不过滤（两个实现同判）")
        void emptyCollectionDeniesInBoth() {
            PermissionRule rule = parser.parse(RESOURCE,
                    "{\"field\":\"departmentId\",\"op\":\"IN\",\"contextVar\":\"departmentIds\"}");
            PermissionRuleContext nobody = new PermissionRuleContext(new PermissionContext(
                    7L, 100L, Set.of(), Set.of(), Set.of(), Set.of("planner"), false));

            assertEquals("(1 = 0)", renderer.render(rule, null, nobody).sql());

            given(root.get("departmentId")).willReturn((Path) path);
            Predicate compiled = compiler.compile(rule, root, builder, nobody);
            assertSame(predicate, compiled, "JPA 侧同样应拿到 builder.disjunction() 的恒假谓词");
            then(builder).should().disjunction();
        }

        @Test
        @DisplayName("NOT_IN 空集合 = 无条件（两实现都不得凭空加条件）")
        void notInWithEmptyCollectionIsNoop() {
            PermissionRuleContext nobody = new PermissionRuleContext(new PermissionContext(
                    7L, 100L, Set.of(), Set.of(), Set.of(), Set.of("planner"), false));
            PermissionRule rule = parser.parse(RESOURCE,
                    "{\"field\":\"departmentId\",\"op\":\"NOT_IN\",\"contextVar\":\"departmentIds\"}");
            assertNull(renderer.render(rule, null, nobody));
            given(root.get("departmentId")).willReturn((Path) path);
            assertNull(compiler.compile(rule, root, builder, nobody));
        }

        @Test
        @DisplayName("列在实体上取不到 → 抛异常（历史静默跳过的地方）")
        void unmappedColumnIsAnErrorNotASkip() {
            given(root.get(anyString())).willThrow(new IllegalArgumentException("Unable to locate Attribute"));
            PermissionRule rule = parser.parse(RESOURCE, "{\"field\":\"tenantId\",\"value\":1}");
            PermissionRuleException ex = assertThrows(PermissionRuleException.class,
                    () -> compiler.compile(rule, root, builder, context));
            assertTrue(ex.getMessage().contains("在实体上不存在"), ex.getMessage());
        }

        @Test
        @DisplayName("ANY_OF 里出现\"无条件\"子节点 → 整组恒真，不加条件")
        void allowAllUnderOrMeansNoCondition() {
            PermissionRule rule = parser.parse(RESOURCE,
                    "{\"op\":\"ANY_OF\",\"children\":[{\"allow\":true},{\"field\":\"tenantId\",\"value\":1}]}");
            // 这里刻意不给 root.get("tenantId") 打桩：ANY_OF 里出现无条件子节点时整组恒真，
            // 编译器应当在碰到 tenantId 之前就短路返回 null。
            // 一旦哪天它真的去取了这个属性（短路失效），strict stubs 会让这个用例变红 —— 这正是本用例要的断言。
            assertNull(compiler.compile(rule, root, builder, context));
            assertNull(renderer.render(rule, null, context));
        }

        @Test
        @DisplayName("分组嵌套渲染成带括号的 AND/OR 组合")
        void rendersNestedGroups() {
            PermissionRule rule = parser.parse(RESOURCE, """
                    {"op":"ALL_OF","children":[
                      {"field":"tenantId","contextVar":"tenantId"},
                      {"op":"ANY_OF","children":[
                         {"field":"creatorId","value":7},
                         {"field":"warehouseId","op":"IN","contextVar":"warehouseIds"}]}]}""");
            RowPermissionPredicate rendered = renderer.render(rule, "inv", context);
            assertEquals("((inv.tenant_id = :dpRule0) AND ((inv.creator_id = :dpRule1) OR (inv.warehouse_id IN (:dpRule2_0, :dpRule2_1))))",
                    rendered.sql());
            assertEquals(4, rendered.parameters().size());
        }
    }

    @Nested
    @DisplayName("求值器：3 层嵌套真值表（Gate 要求）")
    class EvaluatorTruthTable {

        private final PermissionRuleEvaluator evaluator = new PermissionRuleEvaluator();

        private final PermissionRule threeLevels = new PermissionRuleParser().parse(RESOURCE, """
                {"op":"ALL_OF","children":[
                  {"field":"tenantId","contextVar":"tenantId"},
                  {"op":"ANY_OF","children":[
                    {"op":"ALL_OF","children":[
                       {"field":"creatorId","value":7},
                       {"field":"quantity","op":"GT","value":5}]},
                    {"field":"warehouseId","op":"IN","contextVar":"warehouseIds"}]}]}""");

        @Test
        @DisplayName("3 层分组对应树深度 4（分组 3 层 + 叶子 1 层）")
        void depthCountsGroupsAndLeaves() {
            assertEquals(4, threeLevels.depth());
        }

        @Test
        @DisplayName("内层 ALL_OF 成立即可放行")
        void innerAllOfPasses() {
            assertTrue(evaluator.matches(threeLevels, Map.of(
                    "tenantId", 100L, "creatorId", 7L, "quantity", 9L, "warehouseId", 3L), context));
        }

        @Test
        @DisplayName("内层不成立但仓库命中 → ANY_OF 兜住")
        void warehouseMembershipPasses() {
            assertTrue(evaluator.matches(threeLevels, Map.of(
                    "tenantId", 100L, "creatorId", 8L, "quantity", 1L, "warehouseId", 2L), context));
        }

        @Test
        @DisplayName("租户不符直接出局（外层 ALL_OF）")
        void otherTenantDenied() {
            assertFalse(evaluator.matches(threeLevels, Map.of(
                    "tenantId", 101L, "creatorId", 7L, "quantity", 9L, "warehouseId", 2L), context));
        }

        @Test
        @DisplayName("两条分支都不成立 → 拒绝")
        void bothBranchesFail() {
            assertFalse(evaluator.matches(threeLevels, Map.of(
                    "tenantId", 100L, "creatorId", 8L, "quantity", 9L, "warehouseId", 99L), context));
        }

        @Test
        @DisplayName("字段缺失/null 按 SQL 语义不成立（IS_NULL 除外）")
        void nullFieldIsNotMatch() {
            assertFalse(evaluator.matches(parser.parse(RESOURCE, "{\"field\":\"creatorId\",\"value\":7}"),
                    Map.of("tenantId", 100L), context));
            assertTrue(evaluator.matches(parser.parse(RESOURCE, "{\"field\":\"creatorId\",\"op\":\"IS_NULL\"}"),
                    Map.of("tenantId", 100L), context));
        }

        @Test
        @DisplayName("数值类型不必两边一致（Long 与 Integer 可比）")
        void numericCoercion() {
            assertTrue(evaluator.matches(parser.parse(RESOURCE, "{\"field\":\"quantity\",\"value\":5}"),
                    Map.of("quantity", 5), context));
            assertTrue(evaluator.matches(parser.parse(RESOURCE, "{\"field\":\"quantity\",\"op\":\"GE\",\"value\":5}"),
                    Map.of("quantity", 5.5d), context));
        }

        @Test
        @DisplayName("deny/allow 的短路语义")
        void denyAndAllowShortCircuit() {
            assertFalse(evaluator.matches(parser.parse(RESOURCE, "{\"deny\":true}"), Map.of("tenantId", 100L), context));
            assertTrue(evaluator.matches(parser.parse(RESOURCE, "{\"allow\":true}"), Map.of(), context));
            assertTrue(evaluator.matches(parser.parse(RESOURCE, "{}"), Map.of(), context), "无条件 = 不额外限制");
        }
    }

    @Nested
    @DisplayName("上下文变量：只认服务端已解析的维度")
    class ContextVariables {

        @Test
        @DisplayName("支持集合与标量；未登记维度不可用")
        void supportedVariables() {
            assertEquals(2, context.collection(PermissionRuleContext.WAREHOUSE_IDS).size());
            assertEquals(100L, context.scalar(PermissionRuleContext.TENANT_ID));
            assertEquals(100L, context.collection(PermissionRuleContext.TENANT_ID).iterator().next(),
                    "标量维度自动包成单元素集合，方便 IN 复用");
            assertFalse(PermissionRuleContext.isSupported("passwordHash"));
            assertTrue(PermissionRuleContext.isCollectionVar(PermissionRuleContext.DEPARTMENT_IDS));
            assertFalse(PermissionRuleContext.isCollectionVar(PermissionRuleContext.TENANT_ID));
        }

        @Test
        @DisplayName("FACTORY 维度当前恒空且被标记为空 → 供 binding 指标暴露（决策 D13）")
        void factoryDimensionIsKnownEmpty() {
            assertTrue(context.isEmpty(PermissionRuleContext.FACTORY_IDS));
            assertFalse(context.isEmpty(PermissionRuleContext.WAREHOUSE_IDS));
        }

        @Test
        @DisplayName("上下文为 null 时求值安全（不抛 NPE）")
        void nullSafe() {
            PermissionRuleContext empty = new PermissionRuleContext(null);
            assertNull(empty.scalar(PermissionRuleContext.USER_ID));
            assertTrue(empty.collection(PermissionRuleContext.WAREHOUSE_IDS).isEmpty());
            assertTrue(empty.isEmpty(PermissionRuleContext.WAREHOUSE_IDS));
        }
    }

    @Nested
    @DisplayName("标识符工具")
    class IdentifierSupport {

        @Test
        @DisplayName("camel/snake 双向转换稳定")
        void convertsNames() {
            assertEquals("warehouseId", PermissionRuleSupport.toCamel("warehouse_id"));
            assertEquals("warehouseId", PermissionRuleSupport.toCamel("warehouseId"));
            assertEquals("warehouse_id", PermissionRuleSupport.toSnake("warehouseId"));
            assertEquals("warehouse_id", PermissionRuleSupport.toSnake("warehouse_id"));
        }

        @Test
        @DisplayName("注入形状与跨表写法都不是合法标识符")
        void rejectsDirtyIdentifiers() {
            for (String bad : List.of("a.b", "a b", "1abc", "a;b", "a--b", "", "a`b")) {
                assertFalse(PermissionRuleSupport.isIdentifier(bad), "应当拒绝：" + bad);
            }
            // null 单独断言：List.of(...) 本身就拒绝 null 元素，混在循环里会在进断言之前抛 NPE，
            // 报出来的堆栈跟"标识符校验"毫无关系，很容易让人跑去改错的地方。
            assertFalse(PermissionRuleSupport.isIdentifier(null), "应当拒绝：null");
            assertTrue(PermissionRuleSupport.isIdentifier("tenant_id"));
        }
    }
}
