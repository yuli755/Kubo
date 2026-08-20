package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * <h1>DataPermissionSpecification 单元测试</h1>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionSpecificationTest {

    @Mock
    private DataPermissionService dataPermissionService;

    @Mock
    private Root<Object> root;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<Object> path;

    @Mock
    private Predicate predicate;

    private DataPermissionSpecification specification;

    @BeforeEach
    void setUp() {
        specification = new DataPermissionSpecification(dataPermissionService);
        PermissionContextHolder.clear();
        lenient().when(builder.disjunction()).thenReturn(predicate);
        // builder.equal(Expression, Expression) 与 builder.equal(Expression, Object) 是两个重载方法，
        // 必须分别 stub，否则 any() 会被编译器解析为 Expression 类型，导致 equal(Expression, Object) 未被匹配
        lenient().when(builder.equal(any(Expression.class), any(Expression.class))).thenReturn(predicate);
        lenient().when(builder.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        lenient().when(root.get(anyString())).thenReturn(path);
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    @Nested
    class ContextAbsentOrBypass {
        @Test
        void returnsEmptyWhenNoContext() {
            List<Predicate> result = specification.buildPredicates(
                    root, builder, "wms.inventory", DataPermissionAction.QUERY
            );
            assertTrue(result.isEmpty(), "无上下文时应返回空列表");
        }

        @Test
        void returnsEmptyWhenBypass() {
            PermissionContext bypassContext = new PermissionContext(
                    1L, 100L, Set.of(), Set.of(), Set.of(), Set.of(), true
            );
            PermissionContextHolder.open(bypassContext);

            List<Predicate> result = specification.buildPredicates(
                    root, builder, "wms.inventory", DataPermissionAction.QUERY
            );
            assertTrue(result.isEmpty(), "绕过权限时应返回空列表");
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

            List<Predicate> result = specification.buildPredicates(
                    root, builder, "unknown.resource", DataPermissionAction.QUERY
            );
            assertTrue(result.isEmpty(), "资源未注册时应返回空列表（向后兼容）");
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

            List<Predicate> result = specification.buildPredicates(
                    root, builder, "wms.inventory", DataPermissionAction.QUERY
            );
            assertEquals(1, result.size(), "无策略且默认拒绝时应返回一个 disjunction 谓词");
            assertSame(predicate, result.get(0));
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

            List<Predicate> result = specification.buildPredicates(
                    root, builder, "wms.inventory", DataPermissionAction.QUERY
            );
            assertEquals(1, result.size(), "TENANT 策略应生成一个谓词");
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

            List<Predicate> result = specification.buildPredicates(
                    root, builder, "wms.inventory", DataPermissionAction.QUERY
            );
            assertTrue(result.isEmpty(), "SYSTEM 策略不应生成 SQL 级谓词（允许所有）");
        }
    }
}