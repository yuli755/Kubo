package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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

    @Mock
    private Root<Object> root;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<Object> path;

    @Mock
    private Predicate disjunction;

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
        lenient().when(builder.disjunction()).thenReturn(disjunction);
        lenient().when(root.get(anyString())).thenReturn(path);
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

    private List<Predicate> build() {
        return specification.buildPredicates(root, builder, RESOURCE, DataPermissionAction.QUERY);
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

            List<Predicate> result = build();

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

            List<Predicate> result = build();

            assertEquals(1, result.size(), "enforce 模式必须真正注入过滤条件");
            then(audit).should(never()).shadow(eq(RESOURCE), eq("would-filter"), any());
        }

        @Test
        @DisplayName("归属列绑不上（实体未映射 tenant_id）→ 空结果集，而不是放行")
        void failsClosedWhenColumnCannotBeBound() {
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);
            given(dataPermissionService.requireResource(RESOURCE)).willReturn(resource());
            given(dataPermissionService.getPolicies(eq(RESOURCE), any(DataPermissionAction.class), any(PermissionContext.class)))
                    .willReturn(List.of(tenantPolicy()));
            given(root.get("tenant_id")).willThrow(new IllegalArgumentException("Unable to locate Attribute"));

            List<Predicate> result = build();

            assertEquals(1, result.size(), "enforce + 绑定失败应返回恒假谓词（查不到而不是全查到）");
            assertSame(disjunction, result.get(0));
            then(audit).should().binding(eq(RESOURCE), eq(DataPermissionAudit.REASON_BINDING_INVALID), contains("tenant_id"));
        }

        @Test
        @DisplayName("资源未注册且配置要求拒绝 → 恒假谓词")
        void deniesUnregisteredResourceWhenConfigured() {
            properties.setMode(DataPermissionProperties.Mode.ENFORCE);
            properties.setDenyUnregistered(true);
            given(dataPermissionService.requireResource(RESOURCE)).willThrow(new IllegalStateException("未注册"));

            List<Predicate> result = build();

            assertEquals(1, result.size());
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

            assertEquals(1, build().size(), "白名单内资源应已按 enforce 注入");
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
