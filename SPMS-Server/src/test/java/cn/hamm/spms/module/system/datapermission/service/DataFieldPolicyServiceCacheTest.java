package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * <h1>DataFieldPolicyService 类内缓存契约测试</h1>
 *
 * <p>该类从 {@code @Cacheable} 迁移到类内 {@code ConcurrentHashMap}（CGLIB 代理会让
 * 框架 CurdService 的 final CRUD 必炸，见类注释）。本测试钉住迁移后的缓存语义：
 * 同 key 重复读只打一次库（含"资源不存在"的负缓存）；{@code clearAllCache/clearCache}
 * 之后必须回源；{@code existsPolicy} 是直查守卫、不走缓存（初始化幂等依赖它读到
 * 刚写入的行）。测试直接 new 真实服务 + mock 仓储，不依赖 Spring 上下文，
 * 也正好复现"非代理裸对象"这一生产形态。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataFieldPolicyService · 类内缓存语义")
@ExtendWith(MockitoExtension.class)
class DataFieldPolicyServiceCacheTest {

    @Mock
    private DataFieldPolicyRepository fieldPolicyRepository;

    /**
     * 带资源 join 的脱敏策略查询（2026-09-18 SQL 分层：那条 JPQL 搬进了专属 Mapper + XML，
     * 服务改注入 Mapper，所以这里跟着换 mock —— 判据（重复读只查一次库）没变）。
     */
    @Mock
    private cn.hamm.spms.module.system.datapermission.mapper.DataFieldPolicyQueryMapper fieldPolicyQueryMapper;

    @Mock
    private DataResourceRepository resourceRepository;

    private DataFieldPolicyService service;

    @BeforeEach
    void setUp() {
        service = new DataFieldPolicyService(
                mock(DataPermissionEventPublisher.class), resourceRepository, fieldPolicyQueryMapper);
        injectField(service, "repository", fieldPolicyRepository);
    }

    @Test
    @DisplayName("按资源+动作重复读只查一次库，第二次走类内缓存")
    void enabledPoliciesByActionCached() {
        DataResourceEntity resource = new DataResourceEntity().setResourceKey("wms.inventory");
        resource.setId(7L);
        given(resourceRepository.getByResourceKey("wms.inventory")).willReturn(resource);
        DataFieldPolicyEntity policy = new DataFieldPolicyEntity().setFieldName("cost_price");
        given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(7L, "QUERY"))
                .willReturn(List.of(policy));

        List<DataFieldPolicyEntity> first = service.getEnabledPoliciesByResourceAndAction("wms.inventory", "QUERY");
        List<DataFieldPolicyEntity> second = service.getEnabledPoliciesByResourceAndAction("wms.inventory", "QUERY");

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(resourceRepository, times(1)).getByResourceKey("wms.inventory");
        verify(fieldPolicyRepository, times(1))
                .findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(7L, "QUERY");
    }

    @Test
    @DisplayName("资源不存在也负缓存：空列表只查一次库，不每次都打 DB")
    void unknownResourceCachedAsEmpty() {
        given(resourceRepository.getByResourceKey("ghost.resource")).willReturn(null);

        assertTrue(service.getEnabledPoliciesByResourceAndAction("ghost.resource", "QUERY").isEmpty());
        assertTrue(service.getEnabledPoliciesByResourceAndAction("ghost.resource", "QUERY").isEmpty());

        verify(resourceRepository, times(1)).getByResourceKey("ghost.resource");
        verifyNoInteractions(fieldPolicyRepository);
    }

    @Test
    @DisplayName("clearAllCache 之后回源：失效路径必须真的打掉缓存")
    void clearAllCacheForcesReload() {
        DataResourceEntity resource = new DataResourceEntity().setResourceKey("wms.inventory");
        resource.setId(7L);
        given(resourceRepository.getByResourceKey("wms.inventory")).willReturn(resource);
        given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(7L, "QUERY"))
                .willReturn(List.of());

        service.getEnabledPoliciesByResourceAndAction("wms.inventory", "QUERY");
        service.clearAllCache();
        service.getEnabledPoliciesByResourceAndAction("wms.inventory", "QUERY");

        verify(fieldPolicyRepository, times(2))
                .findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(7L, "QUERY");
    }

    @Test
    @DisplayName("clearCache(resourceKey) 全清（与原 @Cacheable allEntries 语义一致）")
    void clearCacheEvictsAllEntries() {
        DataResourceEntity resource = new DataResourceEntity().setResourceKey("wms.inventory");
        resource.setId(7L);
        given(resourceRepository.getByResourceKey("wms.inventory")).willReturn(resource);
        given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(anyLong(), anyString()))
                .willReturn(List.of());

        service.getEnabledPoliciesByResourceAndAction("wms.inventory", "QUERY");
        service.clearCache("wms.inventory");
        service.getEnabledPoliciesByResourceAndAction("wms.inventory", "QUERY");

        verify(fieldPolicyRepository, times(2))
                .findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(7L, "QUERY");
    }

    @Test
    @DisplayName("按字段+角色重复读只查一次库")
    void fieldRolePoliciesCached() {
        DataFieldPolicyEntity policy = new DataFieldPolicyEntity().setFieldName("secret").setMaskType("FULL");
        given(fieldPolicyQueryMapper.findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue(
                "crm.customer", "secret", "SALES")).willReturn(List.of(policy));

        service.getPoliciesByResourceFieldRole("crm.customer", "secret", "SALES");
        service.getPoliciesByResourceFieldRole("crm.customer", "secret", "SALES");

        verify(fieldPolicyQueryMapper, times(1))
                .findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue("crm.customer", "secret", "SALES");
    }

    @Test
    @DisplayName("existsPolicy 直查仓储、不走缓存：初始化幂等守卫必须读到刚写入的行")
    void existsPolicyBypassesCache() {
        given(fieldPolicyRepository.existsByResourceIdAndFieldName(7L, "cost_price")).willReturn(true);

        assertTrue(service.existsPolicy(7L, "cost_price"));
        assertFalse(service.existsPolicy(7L, "unknown_field"));

        verify(fieldPolicyRepository).existsByResourceIdAndFieldName(7L, "cost_price");
        verify(fieldPolicyRepository).existsByResourceIdAndFieldName(7L, "unknown_field");
    }

    /** CurdService 的 repository 是 protected 字段注入，测试里反射塞 mock */
    private static void injectField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
