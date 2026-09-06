package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * <h1>主体谓词的列名解析用例（租户 / 负责人 / 部门 / 工厂 / 仓库）</h1>
 *
 * <p>这批用例存在的理由很具体：既有的 {@code DataPermissionSpecificationTest} 把
 * {@code root.get(anyString())} 一律 stub 成"取得到"，于是<b>任何实体上都取不到的名字</b>
 * （真实 Hibernate 会抛 {@code IllegalArgumentException}）在那份测试里永远暴露不出来 ——
 * 列名 / 属性名写错这一整类静默空转就这么被 mock 的乐观假设盖住了。</p>
 *
 * <p>这里刻意用<b>两个不同的</b> mock 谓词：{@code equality}（真正拼出来的过滤条件）与
 * {@code denial}（{@code builder.disjunction()}，恒假）。绑不上时框架必须给出 {@code denial}，
 * 而不是返回空列表 —— 返回空列表 = 完全不过滤 = 数据全量可见。若两者共用同一个 mock，
 * 这条区分就断言不出来（本仓的测试原先正是共用的，这也是它看不见该看见的东西的原因之一）。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataPermissionSpecification · 归属列取不到时不得静默放开")
class DataPermissionSubjectColumnResolutionTest {

    private static final String KEY = "asset.material";

    @Mock
    private DataPermissionService dataPermissionService;

    @Mock
    private Root<Object> root;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<Object> tenantPath;

    @Mock
    private Predicate equality;

    @Mock
    private Predicate denial;

    private DataPermissionSpecification specification;

    @BeforeEach
    void setUp() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        specification = new DataPermissionSpecification(
                dataPermissionService, properties, mock(DataPermissionAudit.class),
                new PermissionRuleParser(), new JpaPermissionRuleCompiler());
        PermissionContextHolder.clear();
        // 恒假与真条件必须是两个对象，否则断言不出「绑不上时到底是拒绝还是放开」
        lenient().when(builder.disjunction()).thenReturn(denial);
        lenient().when(builder.equal(any(Expression.class), any(Expression.class))).thenReturn(equality);
        lenient().when(builder.equal(any(Expression.class), any(Object.class))).thenReturn(equality);
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(1L, 100L, Set.of(7L), Set.of(3L), Set.of(5L), Set.of("buyer"), false);
    }

    /**
     * 打开上下文再打桩：{@code apply()} 第一步就取 {@code PermissionContextHolder.current()}，
     * 没有上下文时它按「定时任务/内部调用」处理——不注入条件、只记账。
     * （本轮第一版就是漏了这一行，三条用例一律返回空列表；空列表在这里与「防护失效」同形，
     * 所以这类"看起来像断言失败"的红其实是最有价值的红：它说明断言真的在盯住注入与否。）
     */
    private void givenResourceAndPolicy(DataResourceEntity resource, DataPolicyEntity policy, PermissionContext context) {
        PermissionContextHolder.open(context);
        given(dataPermissionService.requireResource(KEY)).willReturn(resource);
        given(dataPermissionService.getPolicies(KEY, DataPermissionAction.QUERY, context)).willReturn(List.of(policy));
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
    @DisplayName("按列名登记 tenant_id：实体只有 tenantId 也必须绑上条件")
    void columnStyleTenantColumnStillFilters() {
        PermissionContext context = context();
        givenResourceAndPolicy(resource("tenant_id", null), policy("TENANT"), context);
        given(root.get("tenant_id")).willThrow(new IllegalArgumentException("Unable to locate Attribute [tenant_id]"));
        given(root.get("tenantId")).willReturn(tenantPath);

        List<Predicate> result = specification.buildPredicates(root, builder, KEY, DataPermissionAction.QUERY);

        assertEquals(1, result.size(), "必须真的拼出一条过滤条件，而不是空列表（空列表 = 不过滤）");
        assertSame(equality, result.get(0), "绑不上时会退回恒假谓词，这里必须是等值条件");
    }

    @Test
    @DisplayName("按属性名登记 tenantId：反方向也能救回列名")
    void attributeStyleTenantColumnResolvesColumn() {
        PermissionContext context = context();
        givenResourceAndPolicy(resource("tenantId", null), policy("TENANT"), context);
        given(root.get("tenantId")).willThrow(new IllegalArgumentException("Unable to locate Attribute [tenantId]"));
        given(root.get("tenant_id")).willReturn(tenantPath);

        List<Predicate> result = specification.buildPredicates(root, builder, KEY, DataPermissionAction.QUERY);

        assertEquals(1, result.size());
        assertSame(equality, result.get(0));
    }

    @Test
    @DisplayName("归属列在实体上不存在：enforce 下必须恒假拒绝，不能返回空列表")
    void unresolvableColumnDeniesInsteadOfReleasing() {
        PermissionContext context = context();
        givenResourceAndPolicy(resource("tenant_created_at", null), policy("TENANT"), context);
        lenient().when(root.get(anyString())).thenThrow(new IllegalArgumentException("Unable to locate Attribute"));

        List<Predicate> result = specification.buildPredicates(root, builder, KEY, DataPermissionAction.QUERY);

        assertEquals(1, result.size(), "本该产生条件却没绑上 = 绑定失败，enforce 下要落进拒绝分支");
        assertSame(denial, result.get(0), "安全侧只能选恒假，返回空列表等于把数据全量放开");
    }
}
