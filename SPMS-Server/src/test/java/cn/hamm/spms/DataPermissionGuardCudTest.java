package cn.hamm.spms;

import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionGuard;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionService;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionResourceResolver;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.JpaPermissionRuleProvider;
import cn.hamm.spms.module.system.datapermission.service.PermissionRuleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * <h1>CUD 数据权限端到端测试</h1>
 *
 * <p>使用 Mockito 模拟 {@code data_resource}/{@code data_policy} 仓储，
 * 验证：资源已注册 + 已配置写入策略 → 写入放行；
 * 未配置写入策略 → 写入被拦截。覆盖 CREATE/UPDATE/DELETE 三个动作。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class DataPermissionGuardCudTest {

    @Mock
    private DataResourceRepository resourceRepository;

    @Mock
    private DataPolicyRepository policyRepository;

    private DataPermissionGuard guard;

    @BeforeEach
    void setUp() {
        PermissionRuleProvider ruleProvider = new JpaPermissionRuleProvider(resourceRepository, policyRepository);
        DataPermissionService service = new DataPermissionService(ruleProvider);
        guard = new DataPermissionGuard(service);
        PermissionContextHolder.clear();
        // 默认返回空策略（无权限）
        lenient().when(policyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(anyLong(), anyString()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    @Test
    void blocksCreateWhenNoWritePolicy() {
        DataResourceEntity resource = new DataResourceEntity().setId(1L).setResourceKey("wms.input").setStatus("ENABLED");
        given(resourceRepository.getByResourceKey("wms.input")).willReturn(resource);
        PermissionContextHolder.open(context(false));

        assertThrows(IllegalStateException.class,
                () -> guard.enforce("wms.input", DataPermissionAction.CREATE));
    }

    @Test
    void allowsCreateWhenWritePolicyExists() {
        DataResourceEntity resource = new DataResourceEntity().setId(1L).setResourceKey("wms.input").setStatus("ENABLED");
        given(resourceRepository.getByResourceKey("wms.input")).willReturn(resource);
        given(policyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "CREATE"))
                .willReturn(List.of(policy("wms_input_write", 1L, DataPermissionAction.CREATE)));
        PermissionContextHolder.open(context(false));

        assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.CREATE));
    }

    @Test
    void allowsUpdateAndDeleteWhenWritePolicyExists() {
        DataResourceEntity resource = new DataResourceEntity().setId(1L).setResourceKey("wms.input").setStatus("ENABLED");
        given(resourceRepository.getByResourceKey("wms.input")).willReturn(resource);
        given(policyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "UPDATE"))
                .willReturn(List.of(policy("wms_input_write", 1L, DataPermissionAction.UPDATE)));
        given(policyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "DELETE"))
                .willReturn(List.of(policy("wms_input_write", 1L, DataPermissionAction.DELETE)));
        PermissionContextHolder.open(context(false));

        assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.UPDATE));
        assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.DELETE));
    }

    @Test
    void bypassUserBypassesGuard() {
        PermissionContextHolder.open(context(true));
        assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.DELETE));
    }

    @Test
    void resolverMapsServiceClassToResourceKey() {
        assertEquals("wms.input", DataPermissionResourceResolver.resolve(
                cn.hamm.spms.module.wms.input.InputService.class));
        assertEquals("mes.order", DataPermissionResourceResolver.resolve(
                cn.hamm.spms.module.mes.order.OrderService.class));
    }

    private DataPolicyEntity policy(String code, long resourceId, DataPermissionAction action) {
        return new DataPolicyEntity()
                .setPolicyCode(code)
                .setResourceId(resourceId)
                .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                .setActionName(action.name())
                .setPriority(100)
                .setEnabled(true);
    }

    private PermissionContext context(boolean bypass) {
        return new PermissionContext(
                1L, 0L, Set.of(), Set.of(), Set.of(), Set.of("planner"), bypass
        );
    }
}