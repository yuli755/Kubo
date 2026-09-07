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
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

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

    @Mock
    private Root<Object> root;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<Object> path;

    @Mock
    private Predicate filterPredicate;

    private DataPermissionProperties properties;
    private DataPermissionSpecification specification;

    @BeforeEach
    void setUp() {
        properties = new DataPermissionProperties();
        specification = new DataPermissionSpecification(dataPermissionService, properties,
                mock(DataPermissionAudit.class), new PermissionRuleParser(), new JpaPermissionRuleCompiler());
        specification.setShadowDiffService(shadowDiffService);
        lenient().when(builder.disjunction()).thenReturn(filterPredicate);
        lenient().when(builder.equal(any(Expression.class), any(Object.class))).thenReturn(filterPredicate);
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(root.getJavaType()).willReturn((Class) DataResourceEntity.class);
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

    private List<Predicate> build() {
        return specification.buildPredicates(root, builder, RESOURCE, DataPermissionAction.QUERY);
    }

    @Test
    @DisplayName("SHADOW：算出了条件就把同一份条件交给比对（不是重新拼一遍）")
    void handsTheSameConditionsToTheDiffService() {
        standOnShadow();

        assertTrue(build().isEmpty(), "shadow 模式绝不能改变查询结果");
        then(shadowDiffService).should().compare(eq(RESOURCE), eq(DataPermissionAction.QUERY),
                eq(DataResourceEntity.class), any(Specification.class), any(PermissionContext.class));
    }

    @Test
    @DisplayName("ENFORCE：一次都不比对 —— 条件已经真注入，双跑只是多打一次全表 COUNT")
    void enforceSkipsDoubleRun() {
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
        given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                .willReturn(List.of(tenantPolicy()));

        build();

        then(shadowDiffService).should(never()).compare(any(), any(), any(), any(), any());
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
        given(root.getJavaType()).willReturn((Class) Object.class);

        build();

        then(shadowDiffService).should(never()).compare(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("比对件没装上：判定结果照旧，只是没有观测")
    void missingDiffServiceDoesNotAffectDecision() {
        standOnShadow();
        specification.setShadowDiffService(null);

        List<Predicate> predicates = assertDoesNotThrow(this::build);

        assertTrue(predicates.isEmpty(), "shadow 档下不该注入条件，与比对件在不在无关");
    }
}
