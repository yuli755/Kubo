package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * <h1>归属迁移服务的用例（B1 余半块）</h1>
 *
 * <p>钉四件事：无上下文拒绝、无 {@code data-permission:ownership} 权限拒绝、有权限时
 * 改归属 + merge + 落审计、目标不是带归属实体时拒绝。归属防篡改的默认语义由
 * {@code AttributionWriteProtectionTest} / {@code TenantAwareEntityTest} 钉，这里是"唯一豁免"。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataOwnershipMigrationService · 归属迁移")
@ExtendWith(MockitoExtension.class)
class DataOwnershipMigrationServiceTest {

    @Entity
    @Table(name = "owned")
    static class OwnedEntity extends TenantAwareEntity<OwnedEntity> {
    }

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityManager entityManager;

    @Mock
    private SystemPermissionService systemPermissionService;

    @Mock
    private UserService userService;

    @Mock
    private DataResourceRepository resourceRepository;

    @Mock
    private DataPermissionDenyLogRecorder denyLogRecorder;

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    private DataOwnershipMigrationService service() {
        DataOwnershipMigrationService service = new DataOwnershipMigrationService(
                entityManagerFactory, systemPermissionService, userService, resourceRepository, denyLogRecorder);
        service.setEntityManager(entityManager);
        return service;
    }

    private static DataOwnershipMigrationService.OwnershipMigrateRequest request() {
        return new DataOwnershipMigrationService.OwnershipMigrateRequest("wms.input", 9L, 1L, 42L);
    }

    private void stubEntityClassIndex() {
        Metamodel metamodel = org.mockito.Mockito.mock(Metamodel.class);
        given(entityManagerFactory.getMetamodel()).willReturn(metamodel);
        Set<ManagedType<?>> managedTypes = new LinkedHashSet<>();
        // 类型必须给足：ManagedType<?>.getJavaType() 返回 Class<?>（捕获类型），
        // willReturn(OwnedEntity.class) 会因不变性匹配不上；定到 ManagedType<OwnedEntity> 后返回 Class<OwnedEntity>。
        ManagedType<OwnedEntity> ownedType = org.mockito.Mockito.mock(ManagedType.class);
        given(ownedType.getJavaType()).willReturn(OwnedEntity.class);
        managedTypes.add(ownedType);
        given(metamodel.getManagedTypes()).willReturn(managedTypes);
        given(resourceRepository.getByResourceKey("wms.input"))
                .willReturn(new DataResourceEntity().setTableName("owned"));
    }

    private static PermissionContext context() {
        return new PermissionContext(7L, 1L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("无请求上下文 → 拒绝")
    void noContextRejects() {
        assertThrows(RuntimeException.class, () -> service().migrate(request()));
        verify(denyLogRecorder, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("无 ownership 权限且非 root → 拒绝，不落迁移审计")
    void unauthorizedUserRejects() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
            given(userService.get(7L)).willReturn(user);
            given(systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP)).willReturn(false);

            assertThrows(RuntimeException.class, () -> service().migrate(request()));
            verify(denyLogRecorder, never()).record(any(), any(), any());
        }
    }

    @Test
    @DisplayName("持有 ownership 权限 → 改归属 + merge + 落审计")
    void ownerMigratesAndAudits() {
        stubEntityClassIndex();
        OwnedEntity target = new OwnedEntity();
        target.setId(9L);
        target.setTenantId(2L);
        target.setCreatorId(3L);

        given(entityManager.find(OwnedEntity.class, 9L)).willReturn(target);

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
            given(userService.get(7L)).willReturn(user);
            given(systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP)).willReturn(true);

            service().migrate(request());
        }

        assertEquals(1L, target.getTenantId());
        assertEquals(42L, target.getCreatorId());
        verify(entityManager).merge(target);
        verify(denyLogRecorder).record(eq("wms.input"), eq(DataPermissionAudit.REASON_OWNERSHIP_MIGRATED), any());
    }

    @Test
    @DisplayName("资源未登记 → 参数错误")
    void unregisteredResourceFails() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
            given(userService.get(7L)).willReturn(user);
            given(systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP)).willReturn(true);
            given(resourceRepository.getByResourceKey("wms.input")).willReturn(null);

            assertThrows(RuntimeException.class, () -> service().migrate(request()));
        }
    }

    @Test
    @DisplayName("目标记录不是带归属实体 → 参数错误")
    void nonTenantAwareTargetFails() {
        stubEntityClassIndex();
        // find(Class, Object) 是泛型方法：直接 willReturn(OwnedEntity.class 版本) 会把 T 锁定为 OwnedEntity，
        // 传 new Object() 会编译不过。这里用原始 Class 匹配器把 T 放宽到 Object，模拟"表索引反查出的类与记录类型不符"。
        given(entityManager.find(any(Class.class), any())).willReturn(new Object());

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            UserEntity user = org.mockito.Mockito.mock(UserEntity.class);
            given(userService.get(7L)).willReturn(user);
            given(systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP)).willReturn(true);

            assertThrows(RuntimeException.class, () -> service().migrate(request()));
        }
        verify(denyLogRecorder, never()).record(any(), any(), any());
    }
}
