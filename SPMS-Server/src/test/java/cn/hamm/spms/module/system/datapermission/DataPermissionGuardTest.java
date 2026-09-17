package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * <h1>数据权限写入守卫测试</h1>
 *
 * <p>验证 {@link DataPermissionGuard} 在 CREATE/UPDATE/DELETE 写入动作下的放行与拦截逻辑。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionGuardTest {

    @Mock
    private DataPermissionService dataPermissionService;

    @Mock
    private DataPermissionAudit audit;

    private cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties properties;

    private DataPermissionGuard guard;

    @BeforeEach
    void setUp() {
        properties = new cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties();
        guard = new DataPermissionGuard(dataPermissionService, properties, audit);
        lenient().when(dataPermissionService.getPolicies(any(), any(), any())).thenReturn(List.of());
        // 守卫现在会先问「这资源纳管了吗」：不 stub 就等于所有用例都跑在「未登记」分支上，
        // 看着绿，其实测的是另一条路径（本轮改动的全部风险都在这条分支上）
        lenient().when(dataPermissionService.findResource(any()))
                .thenReturn(new cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity()
                        .setId(1L).setResourceKey("wms.input").setStatus("ENABLED"));
        PermissionContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    @Nested
    class Enforcement {
        @Test
        void allowsWhenPolicyExists() {
            PermissionContextHolder.open(context(false));
            given(dataPermissionService.getPolicies(eq("wms.input"), eq(DataPermissionAction.CREATE), any()))
                    .willReturn(List.of(policy()));

            assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.CREATE));
        }

        @Test
        void blocksWhenNoPolicy() {
            PermissionContextHolder.open(context(false));
            given(dataPermissionService.getPolicies(eq("wms.input"), eq(DataPermissionAction.UPDATE), any()))
                    .willReturn(List.of());

            // 类型只断到 RuntimeException：FORBIDDEN.show 抛的是框架异常（类名不在本仓可见），
            // 契约是"消息里写清了原因与下一步" —— 与 TenantContextResolverTest 同一口径
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> guard.enforce("wms.input", DataPermissionAction.UPDATE));
            assertTrue(error.getMessage().contains("数据权限不足"), error.getMessage());
            // 拒答同时要有 deny 计数：否则"放开后拦了多少"只能翻日志回答
            verify(audit).deny(eq("wms.input"),
                    eq(DataPermissionAudit.REASON_WRITE_DENIED), anyString());
        }

        @Test
        void allowsBypassUser() {
            PermissionContextHolder.open(context(true));

            assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.DELETE));
        }

        @Test
        void allowsNoContext() {
            assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.DELETE));
        }
    }

    @Nested
    class CreateUpdateDelete {
        @Test
        void appliesToOneActionAtLeast() {
            PermissionContextHolder.open(context(false));
            given(dataPermissionService.getPolicies(eq("wms.input"), any(), any()))
                    .willReturn(List.of(policy()));

            assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.DELETE));
        }
    }

    private DataPolicyEntity policy() {
        return new DataPolicyEntity().setPolicyCode("wms_input_create").setEnabled(true);
    }

    private PermissionContext context(boolean bypass) {
        return new PermissionContext(
                1L, 0L, Set.of(), Set.of(), Set.of(), Set.of("planner"), bypass
        );
    }
}