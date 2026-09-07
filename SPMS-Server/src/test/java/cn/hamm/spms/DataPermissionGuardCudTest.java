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

    @Mock
    private cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit audit;

    private cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties properties;

    private DataPermissionGuard guard;

    @BeforeEach
    void setUp() {
        PermissionRuleProvider ruleProvider = new JpaPermissionRuleProvider(resourceRepository, policyRepository);
        DataPermissionService service = new DataPermissionService(ruleProvider);
        // 默认 mode=shadow、deny-unregistered=false，与 application.yml 的出厂值一致：
        // 用例断言的是「这套配置下会怎样」，而不是「随便一个配置下会怎样」
        properties = new cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties();
        guard = new DataPermissionGuard(service, properties, audit);
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

        // 类型只断到 RuntimeException：FORBIDDEN.show 抛的是框架异常（类名不在本仓可见），
        // 契约是「消息里写清了原因与下一步」—— 与 TenantContextResolverTest 同一口径
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> guard.enforce("wms.input", DataPermissionAction.CREATE));
        // 只断"抛了"是不够的：资源没登记也抛，但那是完全不同的原因
        assertTrue(error.getMessage().contains("数据权限不足"), error.getMessage());
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

    /**
     * OFF 档必须<em>什么都不做</em>：连资源表都不查。
     *
     * <p>这一条是回滚能力的全部意义 —— 之前写侧不看 mode，于是「把配置改回 off」并不能让保存路径
     * 回到改造前的行为（资源没登记 / 没配 UPDATE 策略就直接 500）。读侧一直看，读写两侧对 OFF
     * 的理解第一次不一致。</p>
     */
    @Test
    void offModeSkipsGuardWithoutTouchingRepositories() {
        properties.setMode(cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties.Mode.OFF);
        PermissionContextHolder.open(context(false));

        assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.UPDATE));
        org.mockito.Mockito.verifyNoInteractions(resourceRepository);
        org.mockito.Mockito.verifyNoInteractions(policyRepository);
    }

    /**
     * 未登记资源 + {@code deny-unregistered=false}：放行，但必须留下计数
     *
     * <p>放行是"现状放开"，计数是"让这件事看得见" —— 没有后者的话，"这个模块其实还没纳管"
     * 在指标里完全不存在，下次出问题时又只能靠猜。</p>
     */
    @Test
    void unregisteredResourceIsAllowedButCounted() {
        PermissionContextHolder.open(context(false));

        assertDoesNotThrow(() -> guard.enforce("wms.input", DataPermissionAction.UPDATE));
        org.mockito.Mockito.verify(audit).shadow(org.mockito.ArgumentMatchers.eq("wms.input"),
                org.mockito.ArgumentMatchers.eq(
                        cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit.REASON_UNREGISTERED),
                org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 未登记资源 + {@code deny-unregistered=true}：拒，且文案要能指向配置项
     */
    @Test
    void unregisteredResourceDeniedWhenDenyUnregistered() {
        properties.setDenyUnregistered(true);
        PermissionContextHolder.open(context(false));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> guard.enforce("wms.input", DataPermissionAction.DELETE));
        assertTrue(error.getMessage().contains("deny-unregistered"),
                "拒绝原因必须指向那个配置项，否则运维只能翻代码：" + error.getMessage());
        org.mockito.Mockito.verify(audit).deny(org.mockito.ArgumentMatchers.eq("wms.input"),
                org.mockito.ArgumentMatchers.eq(
                        cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit.REASON_UNREGISTERED),
                org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 已登记但当前动作没有任何策略：仍然拒（这一条<em>不</em>随本轮变化 —— shadow 档也要能暴露缺口），
     * 但异常文案要说出"是不是刚进 enforce 名单"这个最常见的原因
     */
    @Test
    void noPolicyForActionStillDeniedInShadowMode() {
        DataResourceEntity resource = new DataResourceEntity().setId(1L).setResourceKey("wms.input").setStatus("ENABLED");
        org.mockito.BDDMockito.given(resourceRepository.getByResourceKey("wms.input")).willReturn(resource);
        PermissionContextHolder.open(context(false));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> guard.enforce("wms.input", DataPermissionAction.UPDATE));
        assertTrue(error.getMessage().contains("enforce"), "文案要给出下一步动作：" + error.getMessage());
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