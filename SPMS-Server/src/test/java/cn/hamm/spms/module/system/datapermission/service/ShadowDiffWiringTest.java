package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 影子双跑的调用点测试（读侧这一半）
 *
 * <p>这一格要钉的不是"比对算得对不对"（{@code ShadowDiffVerdictTest} 管那个），而是
 * <b>比对有没有被叫起来</b>。放开前置检查 {@code enforce-readiness} 要求差异表里有
 * {@code EXPECTED_FILTER} 正向样本，而全仓只有这里会写那张表：接线一断，
 * "无证据"就永远红，逐资源放开这条流程事实上卡死，而面板上看起来像"还没跑够几天"。</p>
 *
 * <p>反过来也要钉住<em>不该跑的时候不跑</em>：enforce 下条件已经真注入，再双跑是多打一次全表 COUNT；
 * 观测件坏了（Bean 没装上）则必须<em>完全不影响判定</em>。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataPermissionSpecification · 影子双跑调用点")
class ShadowDiffWiringTest {

    private static final String RESOURCE = "wms.inventory";

    @Mock
    private DataPermissionService dataPermissionService;

    @Mock
    private ShadowDiffService shadowDiffService;





    private DataPermissionProperties properties;
    private DataPermissionSpecification specification;

    @BeforeEach
    void setUp() {
        properties = new DataPermissionProperties();
        specification = new DataPermissionSpecification(dataPermissionService, properties,
                mock(DataPermissionAudit.class), new PermissionRuleParser(), new JpaPermissionRuleCompiler());
        specification.setShadowDiffService(shadowDiffService);
        PermissionContextHolder.open(new PermissionContext(1L, 100L, Set.of(1L), Set.of(), Set.of(),
                Set.of("planner"), false));
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private DataResourceEntity resource() {
        return new DataResourceEntity()
                .setResourceKey(RESOURCE)
                .setTableName("inventory")
                .setTenantColumn("tenant_id")
                .setStatus("ENABLED");
    }

    private DataPolicyEntity tenantPolicy() {
        return new DataPolicyEntity().setSubjectType("TENANT").setActionName("QUERY").setEnabled(true);
    }

    private void standOnShadow() {
        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
        given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                .willReturn(List.of(tenantPolicy()));
    }

    /**
     * 走一次注入，返回 wrapper 的 WHERE 片段（空串 = 不注入任何条件）
     *
     * @return WHERE 片段
     * @apiNote J2 起影子线拿到的实体类是**调用方直接给的**（旧实现从 {@code root.getJavaType()} 取）：
     * 引擎不再持有 Criteria，影子比对自己那份 Specification 仍然是 JPA 的（影子线自持）。
     */
    private String build() {
        return buildWith(DataResourceEntity.class);
    }

    private String buildWith(Class<?> entityClass) {
        QueryWrapper<DataResourceEntity> wrapper = new QueryWrapper<>();
        specification.applyQuery(wrapper, entityClass, RESOURCE, DataPermissionAction.QUERY);
        return java.util.Objects.requireNonNullElse(wrapper.getSqlSegment(), "");
    }

    @Test
    @DisplayName("SHADOW：算出了条件就把同一份条件交给比对（不是重新拼一遍）")
    void handsTheSameConditionsToTheDiffService() {
        standOnShadow();

        assertTrue(build().isEmpty(), "shadow 模式绝不能改变查询结果");
        then(shadowDiffService).should().compare(eq(RESOURCE), eq(DataPermissionAction.QUERY),
                eq(DataResourceEntity.class), any(Specification.class), any(PermissionContext.class),
                any(ShadowDiffService.OrmParityProbe.class));
    }

    @Test
    @DisplayName("对账探针带的是登记表里的表名与同一批树（比对侧不许再按策略算一遍）")
    void probeCarriesRegisteredTableAndTheSameTrees() {
        standOnShadow();

        build();

        ArgumentCaptor<ShadowDiffService.OrmParityProbe> captor =
                ArgumentCaptor.forClass(ShadowDiffService.OrmParityProbe.class);
        then(shadowDiffService).should().compare(eq(RESOURCE), eq(DataPermissionAction.QUERY),
                eq(DataResourceEntity.class), any(Specification.class), any(PermissionContext.class), captor.capture());
        ShadowDiffService.OrmParityProbe probe = captor.getValue();

        assertNotNull(probe, "合一之后影子比对应该拿到第三个 COUNT 的输入；null 意味着 §19.36 那条链又断了");
        assertEquals("inventory", probe.tableAlias(),
                "表名必须是 data_resource.table_name 那一列，不是从实体名推的（§19.34 写进基类注释的坑）");
        assertEquals(1, probe.rules().size(), "一条策略一棵树；比对侧重建一遍就是第四份判据");
        assertFalse(probe.rules().get(0) instanceof cn.hamm.spms.module.system.datapermission.rule.PermissionRule.AllowAll,
                "不参与行级过滤的主体混进对账树，就会拿「两边都不加条件」冒充「一致」");
        assertTrue(probe.usable(), "表名/树/上下文齐了才可对账");
    }

    @Test
    @DisplayName("只有一条 ROLE 策略时连比对都不叫（没有可比的条件，就别数出一个假的一致）")
    void nothingToCompareMeansNoCallAtAll() {
        properties.setMode(DataPermissionProperties.Mode.SHADOW);
        given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
        given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                .willReturn(List.of(new DataPolicyEntity().setSubjectType("ROLE").setSubjectId(9L)
                        .setActionName("QUERY").setEnabled(true)));

        assertTrue(build().isEmpty());

        then(shadowDiffService).should(never()).compare(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ENFORCE：一次都不比对 —— 条件已经真注入，双跑只是多打一次全表 COUNT")
    void enforceSkipsDoubleRun() {
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
        given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                .willReturn(List.of(tenantPolicy()));

        build();

        then(shadowDiffService).should(never()).compare(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("OFF：连策略都不查，更不比对（回滚位必须真的是回滚位）")
    void offDoesNotEvenAskForPolicies() {
        properties.setMode(DataPermissionProperties.Mode.OFF);

        assertTrue(build().isEmpty());
        then(dataPermissionService).shouldHaveNoInteractions();
        then(shadowDiffService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("拿不到可 COUNT 的实体类（投影/tuple root）：跳过，不把 NPE 丢进异步队列")
    void projectionRootIsSkipped() {
        standOnShadow();

        buildWith(Object.class);

        then(shadowDiffService).should(never()).compare(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("比对件没装上：判定结果照旧，只是没有观测")
    void missingDiffServiceDoesNotAffectDecision() {
        standOnShadow();
        specification.setShadowDiffService(null);

        String predicates = assertDoesNotThrow(this::build);

        assertTrue(predicates.isEmpty(), "shadow 档下不该注入条件，与比对件在不在无关");
    }
}
