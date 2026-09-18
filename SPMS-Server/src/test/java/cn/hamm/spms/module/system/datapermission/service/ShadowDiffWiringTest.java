package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 影子比对的调用点测试（wrapper 这一半）
 *
 * <p>这一格要钉的不是"比对算得对不对"（{@code ShadowDiffVerdictTest} 管结论分类、
 * {@code ShadowDiffServiceCountTest} 管两次 COUNT 的取数与落库），而是
 * <b>比对有没有被叫起来、以及交出去的是哪一份条件</b>。放开前置检查 {@code enforce-readiness}
 * 要求差异表里有 {@code EXPECTED_FILTER} 正向样本，而 wrapper 与拦截器这两条注入路径是全仓
 * 唯一会写那张表的地方：接线一断，"无证据"就永远红，逐资源放开这条流程事实上卡死，
 * 而面板上看起来像"还没跑够几天"。</p>
 *
 * <p>反过来也要钉住<em>不该跑的时候不跑</em>：enforce 下条件已经真注入，再双跑是多打一次全表 COUNT；
 * 观测件坏了（Bean 没装上）则必须<em>完全不影响判定</em>。</p>
 *
 * <p><b>J4（2026-09-18）改了这里的断言对象</b>：原来交出去的是 JPA {@code Specification} 加一个
 * {@code OrmParityProbe}（第三个 COUNT 的输入），断言要看懂两种 JPA 类型；现在交出去的就是
 * <b>刚刚渲染、也正要挂到 wrapper 上的那批片段</b>（{@link RowPermissionPredicate}）——
 * 一条渲染路径，一份取证输入，断言直接读片段文本。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataPermissionSpecification · 影子比对调用点")
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
                mock(DataPermissionAudit.class), new PermissionRuleParser(), new SqlPermissionRuleRenderer());
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
     * @apiNote 实体类是调用方直接给的（J2 起引擎不再持有 Criteria）；J4 起它同时是影子比对
     * "数哪张表"的输入，不再有第二份 Specification 去重新算一遍。
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
        ArgumentCaptor<List<RowPermissionPredicate>> captor = ArgumentCaptor.forClass(List.class);
        then(shadowDiffService).should().compare(eq(RESOURCE), eq(DataPermissionAction.QUERY),
                eq(DataResourceEntity.class), captor.capture(), any(PermissionContext.class));

        List<RowPermissionPredicate> handed = captor.getValue();
        assertEquals(1, handed.size(), "一条可渲染的策略 ⇒ 一个片段；比对侧不该看到重算出来的第二份");
        assertEquals("(tenant_id = :dpRule0)", handed.get(0).sql(),
                "交给比对的就是注入时渲染的那一份：不带别名（数的是实体仓储的裸表查询），列名走白名单");
        assertEquals(100L, handed.get(0).parameters().get("dpRule0"), "参数值原样带过去，比对侧不重算上下文");
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

        then(shadowDiffService).should(never()).compare(any(), any(), any(), any(), any());
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
    @DisplayName("比对件没装上：判定结果照旧，只是没有观测")
    void missingDiffServiceDoesNotAffectDecision() {
        standOnShadow();
        specification.setShadowDiffService(null);

        String predicates = assertDoesNotThrow(this::build);

        assertTrue(predicates.isEmpty(), "shadow 档下不该注入条件，与比对件在不在无关");
    }
}
