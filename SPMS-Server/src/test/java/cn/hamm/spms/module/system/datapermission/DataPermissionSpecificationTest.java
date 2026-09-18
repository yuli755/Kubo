package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * <h1>DataPermissionSpecification 单元测试</h1>
 *
 * <p>2026-09-18 J2：注入侧从 Criteria 换成 wrapper 上的 SQL 片段，本类断言的对象随之变成片段文本。
 * <b>不再覆盖的一件事</b>："实体上没有这一列"这类绑定失败 —— 渲染器只看规则与登记，不查实体形状，
 * 所以它照样能产出片段（数据库会以"列不存在"报错，是响亮失败而不是静默放行）；
 * 这类判据留在影子比对与 enforce-readiness 那两条线上，见 docs/去JPA决策评估.md §10.8.4。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionSpecificationTest {

    @Mock
    private DataPermissionService dataPermissionService;

    private DataPermissionSpecification specification;

    @BeforeEach
    void setUp() {
        // 本测试类验证的是"给定策略如何拼成谓词"，因此固定为 ENFORCE；
        // 模式语义（off/shadow/enforce）另有 DataPermissionModeSemanticsTest 覆盖。
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        specification = new DataPermissionSpecification(
                dataPermissionService, properties, mock(DataPermissionAudit.class),
                new cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser(),
                new cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer());
        PermissionContextHolder.clear();
    }

    /**
     * 走一次注入，返回被挂条件的那只 wrapper
     *
     * @param resourceKey 资源标识
     * @return wrapper
     * @apiNote J2 之前这个位置是 {@code buildPredicates(root, builder, …)}：断言的对象是 Criteria 谓词，
     * 要靠 mock 出 {@code Root}/{@code CriteriaBuilder} 才能观察；J4 之后连那套 API 都不在了。
     * 现在观察对象是 wrapper 上的 SQL 片段，断言直接读片段文本 —— 少了两层 mock，测的还是同一件事（注入了什么）。
     */
    private QueryWrapper<DataResourceEntity> applyQuery(String resourceKey) {
        QueryWrapper<DataResourceEntity> wrapper = new QueryWrapper<>();
        specification.applyQuery(wrapper, DataResourceEntity.class, resourceKey, DataPermissionAction.QUERY);
        return wrapper;
    }

    /**
     * 注入结果：wrapper 的 WHERE 片段（空串 = 一条条件都没加）
     *
     * @param wrapper 查询条件
     * @return 片段
     */
    private static String where(QueryWrapper<?> wrapper) {
        return Objects.requireNonNullElse(wrapper.getSqlSegment(), "");
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    @Nested
    class ContextAbsentOrBypass {
        @Test
        void returnsEmptyWhenNoContext() {
            assertTrue(where(applyQuery("wms.inventory")).isEmpty(), "无上下文时不应注入任何条件");
        }

        @Test
        void returnsEmptyWhenBypass() {
            PermissionContext bypassContext = new PermissionContext(
                    1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), true
            );
            PermissionContextHolder.open(bypassContext);

            assertTrue(where(applyQuery("wms.inventory")).isEmpty(), "绕过权限时不应注入任何条件");
        }
    }

    @Nested
    class ResourceNotRegistered {
        @Test
        void returnsEmptyWhenResourceNotFound() {
            PermissionContext context = new PermissionContext(
                    1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false
            );
            PermissionContextHolder.open(context);

            given(dataPermissionService.requireResource("unknown.resource"))
                    .willThrow(new IllegalStateException("资源未注册"));

            assertTrue(where(applyQuery("unknown.resource")).isEmpty(), "资源未注册时不注入（向后兼容）");
        }
    }

    @Nested
    class DefaultDenyWhenNoPolicies {
        @Test
        void returnsDisjunctionWhenNoPoliciesAndDenyByDefault() {
            PermissionContext context = new PermissionContext(
                    1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false
            );
            PermissionContextHolder.open(context);

            DataResourceEntity resource = new DataResourceEntity()
                    .setResourceKey("wms.inventory")
                    .setTableName("inventory")
                    .setTenantColumn("tenant_id")
                    .setStatus("ENABLED");

            given(dataPermissionService.requireResource("wms.inventory")).willReturn(resource);
            given(dataPermissionService.getPolicies("wms.inventory", DataPermissionAction.QUERY, context))
                    .willReturn(List.of());
            given(dataPermissionService.shouldDenyByDefault("wms.inventory", context))
                    .willReturn(true);

            QueryWrapper<DataResourceEntity> wrapper = applyQuery("wms.inventory");

            assertTrue(where(wrapper).contains(RowPermissionPredicate.DENY_SQL),
                    "无策略且默认拒绝时应给恒假条件（1 = 0），而不是放开原查询");
        }
    }

    @Nested
    class TenantIsolation {
        @Test
        void buildsTenantPredicateWhenSystemPolicyPresent() {
            PermissionContext context = new PermissionContext(
                    1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false
            );
            PermissionContextHolder.open(context);

            DataResourceEntity resource = new DataResourceEntity()
                    .setResourceKey("wms.inventory")
                    .setTableName("inventory")
                    .setTenantColumn("tenant_id")
                    .setStatus("ENABLED");

            DataPolicyEntity policy = new DataPolicyEntity()
                    .setSubjectType("TENANT")
                    .setActionName("QUERY")
                    .setEnabled(true);

            given(dataPermissionService.requireResource("wms.inventory")).willReturn(resource);
            given(dataPermissionService.getPolicies("wms.inventory", DataPermissionAction.QUERY, context))
                    .willReturn(List.of(policy));

            QueryWrapper<DataResourceEntity> wrapper = applyQuery("wms.inventory");

            assertTrue(where(wrapper).contains("tenant_id"), "TENANT 策略应生成 tenant_id 条件");
        }
    }

    @Nested
    class SystemAndRolePoliciesReturnNull {
        @Test
        void systemPolicyReturnsNullPredicate() {
            PermissionContext context = new PermissionContext(
                    1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false
            );
            PermissionContextHolder.open(context);

            DataResourceEntity resource = new DataResourceEntity()
                    .setResourceKey("wms.inventory")
                    .setTableName("inventory")
                    .setTenantColumn("tenant_id")
                    .setStatus("ENABLED");

            DataPolicyEntity systemPolicy = new DataPolicyEntity()
                    .setSubjectType("SYSTEM")
                    .setActionName("QUERY")
                    .setEnabled(true);

            given(dataPermissionService.requireResource("wms.inventory")).willReturn(resource);
            given(dataPermissionService.getPolicies("wms.inventory", DataPermissionAction.QUERY, context))
                    .willReturn(List.of(systemPolicy));

            assertTrue(where(applyQuery("wms.inventory")).isEmpty(), "SYSTEM 策略不应生成条件（允许所有）");
        }
    }
}