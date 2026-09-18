package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * <h1>数据权限模式语义测试（决策 D2 / D3 的可执行契约）</h1>
 *
 * <p>灰度三态是本项目"从空转到真正防护"的唯一安全路径，因此这里锁的不是实现细节，
 * 而是三态各自<b>必须</b>与<b>绝不能</b>做的动作：</p>
 * <ul>
 *   <li>{@code OFF} —— 零参与（连策略都不查），保证回滚位真正可回滚；</li>
 *   <li>{@code SHADOW} —— 算得出差异但<b>不改变结果</b>，只打点；</li>
 *   <li>{@code ENFORCE} —— 绑定失败按空结果集处理（fail-closed），不静默放行。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionModeSemanticsTest {

    private static final String RESOURCE = "wms.inventory";

    @Mock
    private DataPermissionService dataPermissionService;

    private DataPermissionProperties properties;
    private DataPermissionAudit audit;
    private DataPermissionSpecification specification;

    @BeforeEach
    void setUp() {
        properties = new DataPermissionProperties();
        audit = mock(DataPermissionAudit.class);
        specification = new DataPermissionSpecification(dataPermissionService, properties, audit,
                new cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser(),
                new cn.hamm.spms.module.system.datapermission.rule.JpaPermissionRuleCompiler());
        PermissionContextHolder.open(new PermissionContext(1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false));
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

    /**
     * 走一次注入，返回 wrapper 的 WHERE 片段（空串 = 不注入任何条件）
     *
     * @return WHERE 片段
     * @apiNote J2 起注入的是 SQL 片段而不是 Criteria 谓词，所以观察对象是片段文本：
     * ShadowDiffService 仍走 JPA（影子线自持），但规格本身不再需要 Root/CriteriaBuilder。
     */
    private String build() {
        QueryWrapper<DataResourceEntity> wrapper = new QueryWrapper<>();
        specification.applyQuery(wrapper, DataResourceEntity.class, RESOURCE, DataPermissionAction.QUERY);
        return java.util.Objects.requireNonNullElse(wrapper.getSqlSegment(), "");
    }

    @Nested
    @DisplayName("OFF：完全短路")
    class Off {

        @Test
        @DisplayName("不查资源、不查策略，直接返回空条件 —— 回滚位必须真的是回滚位")
        void shortCircuitsWithoutTouchingPolicyStore() {
            properties.setMode(DataPermissionProperties.Mode.OFF);

            assertTrue(build().isEmpty(), "off 模式不应注入任何条件");
            then(dataPermissionService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("SHADOW：算得出、但不改结果")
    class Shadow {

        @Test
        @DisplayName("本应过滤的查询仍然返回全量（不注入），差异只进打点")
        void computesButDoesNotApply() {
            properties.setMode(DataPermissionProperties.Mode.SHADOW);
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(tenantPolicy()));

            String result = build();

            assertTrue(result.isEmpty(), "shadow 模式绝不能改变查询结果");
            then(audit).should().shadow(eq(RESOURCE), eq("would-filter"), any());
        }

        @Test
        @DisplayName("默认拒绝在 shadow 下也只记录，不返回恒假谓词")
        void defaultDenyIsOnlyRecorded() {
            properties.setMode(DataPermissionProperties.Mode.SHADOW);
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of());
            given(dataPermissionService.shouldDenyByDefault(RESOURCE, PermissionContextHolder.require())).willReturn(true);

            assertTrue(build().isEmpty(), "shadow 模式下默认拒绝不应真正生效");
            then(audit).should().shadow(eq(RESOURCE), eq(DataPermissionAudit.REASON_NO_POLICY), any());
            then(audit).should(never()).deny(eq(RESOURCE), eq(DataPermissionAudit.REASON_NO_POLICY), any());
        }
    }

    @Nested
    @DisplayName("ENFORCE：真正注入，且失败即关")
    class Enforce {

        @Test
        @DisplayName("正常策略 → 注入条件，不打 shadow 点")
        void appliesPredicates() {
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(tenantPolicy()));

            String result = build();

            assertFalse(result.isEmpty(), "enforce 模式必须真正注入过滤条件");
            then(audit).should(never()).shadow(eq(RESOURCE), eq("would-filter"), any());
        }

        @Test
        @DisplayName("归属列写错（实体没有这一列）→ 片段照样产出，由数据库响亮报错；不许静默放行")
        void wrongColumnIsNotSilentlyReleased() {
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(tenantPolicy()));

            String result = build();

            // J2 之前这条靠 JPA 的 root.get() 抛异常发现"实体没这一列"，于是恒假（查不到）。
            // 现在渲染器不查实体形状：片段照样写进 tenant_id，列不存在时由数据库直接报错 ——
            // 失败是响亮的（SQL 异常 / 500），不是静默放行。这类登记错误由 enforce 放开前的
            // readiness 面板按 binding 指标清零（判据不再藏在 CURD 引擎里，见 docs §10.8.4）。
            assertTrue(result.contains("tenant_id"), "片段必须写进 tenant_id，让数据库在列不存在时直接报错");
            then(audit).should(never()).shadow(eq(RESOURCE), eq("would-filter"), any());
        }

        @Test
        @DisplayName("FACTORY 策略命中但实体没有工厂列 → enforce 仍恒假（不静默放开）")
        void factoryPolicyWithoutColumnFailsClosed() {
            /**
             * 这条用例存在的理由是 D13 的开关语义：把 {@code factory-dimension-enabled} 打开后，
             * {@code DimensionGuard} 不再拒 FACTORY 策略，于是"策略能存下来"与"策略能生效"第一次
             * 变成两件事。工厂归属列在业务实体上目前并不存在（{@code SubjectDimensionRules} 给的兜底
             * 候选列名是 {@code factoryId}），JPA 时代靠 {@code root.get} 抛异常把这种策略读成"绑不上"⇒恒假。
             * J2 之后渲染器不查实体形状：条件照写进 {@code factory_id}，列不存在由数据库报错 ——
             * 关键是**永远不能变成"不追加条件"**，那等于所有人都能看到全部行，比没开开关更糟。
             */
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);
            // factoryIds 必须非空：subjectId 不在集合里时策略在 getPolicies 阶段就被过滤，
            // 走不到绑定环节，那样这条用例测的就不是"绑不上列"了。
            PermissionContextHolder.open(new PermissionContext(
                    1L, 100L, Set.of(), Set.of(7L), Set.of(), Set.of("planner"), false));
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(new DataPolicyEntity()
                            .setSubjectType("FACTORY").setSubjectId(7L).setActionName("QUERY").setEnabled(true)));
            String result = build();

            assertTrue(result.contains("factory_id") || result.contains("factoryId"),
                    "工厂策略必须产出条件（列不存在时数据库报错），绝不能变成「不追加条件」（=全员可见）");
        }

        @Test
        @DisplayName("同一情形在 SHADOW 下只记账、结果不变")
        void factoryBindingGapIsOnlyRecordedInShadow() {
            properties.setMode(DataPermissionProperties.Mode.SHADOW);
            PermissionContextHolder.open(new PermissionContext(
                    1L, 100L, Set.of(), Set.of(7L), Set.of(), Set.of("planner"), false));
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(new DataPolicyEntity()
                            .setSubjectType("FACTORY").setSubjectId(7L).setActionName("QUERY").setEnabled(true)));

            assertTrue(build().isEmpty(), "shadow 不改变结果：只把「该拦」记下来");
            then(audit).should().shadow(eq(RESOURCE), eq("would-filter"), any());
        }

        @Test
        @DisplayName("资源未注册且配置要求拒绝 → 恒假谓词")
        void deniesUnregisteredResourceWhenConfigured() {
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);
            properties.setDenyUnregistered(true);
            given(dataPermissionService.requireResource(RESOURCE)).willThrow(new IllegalStateException("未注册"));

            assertTrue(build().contains(RowPermissionPredicate.DENY_SQL), "未注册资源必须恒假");
            then(audit).should().deny(eq(RESOURCE), eq(DataPermissionAudit.REASON_UNREGISTERED), any());
        }

        @Test
        @DisplayName("SYSTEM 策略语义是放行：不生成条件也不误判为绑定失败")
        void systemPolicyMeansAllowAll() {
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(new DataPolicyEntity().setSubjectType("SYSTEM").setActionName("QUERY").setEnabled(true)));

            assertTrue(build().isEmpty(), "SYSTEM 策略应放行且不触发绑定告警");
            then(audit).should(never()).binding(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("无上下文（定时任务）→ 不注入但必须记账，防止长期当旁路用")
        void noContextIsRecordedAsBindingIssue() {
            PermissionContextHolder.clear();
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);

            assertTrue(build().isEmpty());
            then(audit).should().binding(eq(RESOURCE), eq("no-permission-context"), any());
        }
    }

    @Nested
    @DisplayName("资源级白名单")
    class PerResourceAllowList {

        @Test
        @DisplayName("全局 shadow，白名单内资源 enforce")
        void allowListOverridesGlobalMode() {
            properties.setMode(DataPermissionProperties.Mode.SHADOW);
            properties.setEnforceResources(Set.of(RESOURCE));
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(tenantPolicy()));

            assertFalse(build().isEmpty(), "白名单内资源应已按 enforce 注入");
        }

        @Test
        @DisplayName("白名单外资源维持全局模式")
        void otherResourcesKeepGlobalMode() {
            properties.setMode(DataPermissionProperties.Mode.SHADOW);
            properties.setEnforceResources(Set.of("asset.material"));
            assertEquals(DataPermissionProperties.Mode.SHADOW, properties.effectiveMode(RESOURCE));
            assertEquals(DataPermissionProperties.Mode.ENFORCE, properties.effectiveMode("asset.material"));
            then(audit).should(never()).deny(anyString(), anyString(), any());
        }
    }
}
