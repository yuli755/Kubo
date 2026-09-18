package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * <h1>主体谓词的列名解析用例（租户 / 负责人 / 部门 / 工厂 / 仓库）</h1>
 *
 * <p>这批用例盯的是同一件事：<b>登记里的列名最终变成了哪一列、有没有变成"不加条件"</b>。</p>
 *
 * <p>2026-09-18 J2：注入侧从 Criteria 谓词换成 wrapper 上的 SQL 片段，判据的形状随之改变，三处要点写在这里，
 * 免得下一个人按旧口径读：</p>
 *
 * <ul>
 *   <li><b>不再是"挑第一个绑得上的候选"</b>：渲染器不查实体形状，{@code tenant_id} 与 {@code tenantId}
 *       都由 {@code toSnake} 归一成 {@code tenant_id}（所以两种写法都能注入，且注入的是同一列）；</li>
 *   <li><b>一跳关联 {@code storage.id} 落到本表外键列</b> {@code storage_id}（Hibernate 对
 *       {@code @ManyToOne.get("id")} 本来也是直取 FK），两跳/非 id 的关联路径仍然抛；</li>
 *   <li><b>"实体上没有这一列"这类错误不再在这一层被拦下</b>：片段照样产出，数据库会以"列不存在"报错
 *       （响亮失败），而 binding 记账与 enforce-readiness 面板负责在放开前清零这类登记 ——
 *       判据从 CURD 引擎搬到了 readiness/shadow 线（docs/去JPA决策评估.md §10.8.4）。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataPermissionSpecification · 归属列最终落到哪一列、会不会变成不加条件")
class DataPermissionSubjectColumnResolutionTest {

    private static final String KEY = "asset.material";

    @Mock
    private DataPermissionService dataPermissionService;

    @Mock
    private DataPermissionAudit audit;

    private DataPermissionSpecification specification;

    @BeforeEach
    void setUp() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        specification = new DataPermissionSpecification(
                dataPermissionService, properties, audit,
                new PermissionRuleParser(), new SqlPermissionRuleRenderer());
        PermissionContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(1L, 100L, Set.of(7L), Set.of(3L), Set.of(5L), Set.of("buyer"), false);
    }

    /**
     * 打开上下文再打桩：{@code applyQuery()} 第一步就取 {@code PermissionContextHolder.current()}，
     * 没有上下文时它按「定时任务/内部调用」处理 —— 不注入条件、只记账。
     * （本轮第一版就是漏了这一行，三条用例一律返回空片段；空片段在这里与「防护失效」同形，
     * 所以这类"看起来像断言失败"的红其实是最有价值的红：它说明断言真的在盯住注入与否。）
     *
     * @param resource 数据资源
     * @param policy   策略
     * @param context  权限上下文
     */
    private void givenResourceAndPolicy(DataResourceEntity resource, DataPolicyEntity policy, PermissionContext context) {
        PermissionContextHolder.open(context);
        given(dataPermissionService.requireResource(KEY)).willReturn(resource);
        given(dataPermissionService.getPolicies(KEY, DataPermissionAction.QUERY, context)).willReturn(List.of(policy));
    }

    /**
     * 走一次注入，返回 wrapper 的 WHERE 片段（空串 = 一条条件都没加 = 放开了全部行）
     *
     * @return WHERE 片段
     */
    private String build() {
        QueryWrapper<DataResourceEntity> wrapper = new QueryWrapper<>();
        specification.applyQuery(wrapper, DataResourceEntity.class, KEY, DataPermissionAction.QUERY);
        return Objects.requireNonNullElse(wrapper.getSqlSegment(), "");
    }

    private DataResourceEntity resource(String tenantColumn, String ownerColumns) {
        return new DataResourceEntity()
                .setResourceKey(KEY)
                .setTableName("material")
                .setTenantColumn(tenantColumn)
                .setOwnerColumns(ownerColumns)
                .setStatus("ENABLED");
    }

    private DataPolicyEntity policy(String subjectType) {
        return new DataPolicyEntity()
                .setSubjectType(subjectType)
                .setActionName("QUERY")
                .setEnabled(true);
    }

    @Test
    @DisplayName("按列名登记 tenant_id：条件落在 tenant_id 上")
    void columnStyleTenantColumnStillFilters() {
        PermissionContext context = context();
        givenResourceAndPolicy(resource("tenant_id", null), policy("TENANT"), context);

        String where = build();

        assertFalse(where.isEmpty(), "必须真的拼出过滤条件，空片段 = 不过滤 = 全量可见");
        assertTrue(where.contains("tenant_id"), "登记的就是 tenant_id：" + where);
    }

    @Test
    @DisplayName("按属性名登记 tenantId：同样落到 tenant_id（渲染器只做 snake，不查实体）")
    void attributeStyleTenantColumnResolvesColumn() {
        PermissionContext context = context();
        givenResourceAndPolicy(resource("tenantId", null), policy("TENANT"), context);

        String where = build();

        assertTrue(where.contains("tenant_id"), "属性名写法要归一成列名：" + where);
    }

    @Test
    @DisplayName("仓库档登记 WAREHOUSE:storage.id → 一跳落到本表外键列 storage_id")
    void warehouseAssociationPathFilters() {
        PermissionContext context = context();
        DataResourceEntity resource = resource("tenant_id", "WAREHOUSE:storage.id");
        DataPolicyEntity policy = policy("WAREHOUSE").setSubjectId(5L);
        givenResourceAndPolicy(resource, policy, context);

        String where = build();

        assertFalse(where.isEmpty(), "关联路径必须能注入，空片段 = 不过滤");
        assertTrue(where.contains("storage_id"), "一跳的 .id 要落到本表外键列：" + where);
        // 关键反证：不许退回写死的 warehouseId —— 本仓没有这一列，撞上去只会得到"列不存在"
        assertFalse(where.contains("warehouse_id"), "登记了 storage.id 就不该再去看 warehouse_id：" + where);
    }

    @Test
    @DisplayName("没登记 WAREHOUSE: 时按兜底候选列找：条件写进 warehouse_id，由数据库判它存不存在")
    void warehouseFallsBackToHardCodedAttributeName() {
        PermissionContext context = context();
        DataPolicyEntity policy = policy("WAREHOUSE").setSubjectId(5L);
        givenResourceAndPolicy(resource("tenant_id", "creatorId"), policy, context);

        String where = build();

        // J2 之前：绑不上 ⇒ 恒假（查不到）。J2 之后：条件写进兜底列名，列不存在时数据库报错。
        // 两种都不会"静默放开"，区别只是失败发生在哪一层（这里刻意把差异钉住，免得被误读成回归）。
        assertTrue(where.contains("warehouse_id"), "兜底候选列要真的写进片段：" + where);
        then(audit).should(never()).deny(any(), any(), any());
    }

    @Test
    @DisplayName("部门档登记 DEPARTMENT:dept_id → 用自己那一项，不抢负责人档")
    void departmentUsesItsOwnEntry() {
        PermissionContext context = context();
        DataPolicyEntity policy = policy("DEPARTMENT").setSubjectId(7L);
        givenResourceAndPolicy(resource("tenant_id", "creatorId,DEPARTMENT:dept_id"), policy, context);

        String where = build();

        assertTrue(where.contains("dept_id"), "部门档要用登记里的 dept_id：" + where);
        assertFalse(where.contains("creator_id"), "部门档不该退到负责人列：" + where);
    }

    @Test
    @DisplayName("登记了一个不存在的列：片段照样产出（数据库报错），且不再被记成 binding 失败")
    void unresolvableColumnIsNotDetectedHere() {
        PermissionContext context = context();
        givenResourceAndPolicy(resource("tenant_created_at", null), policy("TENANT"), context);

        String where = build();

        assertTrue(where.contains("tenant_created_at"), "片段按登记内容产出：" + where);
        then(audit).should(never()).binding(any(), any(), any());
    }

    @Test
    @DisplayName("关联路径超过一跳：渲染器仍旧直接抛，不许编出查不到数据的条件")
    void multiHopPathStillThrows() {
        PermissionContext context = context();
        DataResourceEntity resource = resource("tenant_id", "WAREHOUSE:storage.factory.id");
        DataPolicyEntity policy = policy("WAREHOUSE").setSubjectId(5L);
        givenResourceAndPolicy(resource, policy, context);

        // 渲染器对两跳路径抛 PermissionRuleException（校验期也可能先拦）；applyQuery 把它变成
        // ENFORCE 下的恒假 + binding 记账 —— 无论在哪一层失败，都必须是"查不到"，不是"全量放开"
        assertTrue(build().contains(RowPermissionPredicate.DENY_SQL),
                "渲染不出来时必须恒假：返回空片段等于把数据全量放开");
        then(audit).should().binding(any(), any(), any());
    }

    @Test
    @DisplayName("上下文里没有这一维的值：恒假，而不是不加条件")
    void missingContextValueDenies() {
        PermissionContext context = new PermissionContext(1L, 100L, Set.of(), Set.of(), Set.of(), Set.of("buyer"), false);
        DataPolicyEntity policy = policy("DEPARTMENT").setSubjectId(7L);
        givenResourceAndPolicy(resource("tenant_id", "creatorId,DEPARTMENT:dept_id"), policy, context);

        assertTrue(build().contains(RowPermissionPredicate.DENY_SQL), "值为空必须退化成恒假，而不是不加条件");
    }
}
