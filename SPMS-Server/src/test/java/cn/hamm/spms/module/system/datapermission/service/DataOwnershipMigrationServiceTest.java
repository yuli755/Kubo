package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.TenantAwareEntity;
import cn.hamm.spms.framework.curd.base.CurdEntity;
import cn.hamm.spms.framework.curd.base.EntityClassIndex;
import cn.hamm.spms.framework.curd.base.EntityRepositories;
import cn.hamm.spms.framework.curd.base.ICurdRepository;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * <h1>归属迁移服务的用例（B1 余半块）</h1>
 *
 * <p>钉四件事：无上下文拒绝、无 {@code data-permission:ownership} 权限拒绝、有权限时
 * 只写归属两列 + 落审计、目标不是带归属实体时拒绝。归属防篡改的默认语义由
 * {@code AttributionWriteProtectionTest} / {@code TenantAwareEntityTest} 钉，这里是"唯一豁免"。</p>
 *
 * <p>J3：JPA 的 {@code EntityManager}/{@code EntityManagerFactory} 替身换成两个真实接缝 ——
 * {@link EntityClassIndex}（资源表名 → 实体类）与 {@link EntityRepositories}（实体类 → 写侧仓储）。
 * 断言也从"实体被就地改写 + merge"改成"UPDATE 语句写了哪两列、写了什么值"，
 * 因为迁移现在就是一条只带归属列的 UPDATE（不碰业务字段，也不触发业务钩子）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataOwnershipMigrationService · 归属迁移")
@ExtendWith(MockitoExtension.class)
class DataOwnershipMigrationServiceTest {

    @TableName("owned")
    static class OwnedEntity extends TenantAwareEntity<OwnedEntity> {
    }

    @Mock
    private EntityRepositories entityRepositories;

    @Mock
    private EntityClassIndex entityClassIndex;

    @Mock
    private ICurdRepository<OwnedEntity> ownedRepository;

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
        return new DataOwnershipMigrationService(
                entityRepositories, entityClassIndex, systemPermissionService, userService,
                resourceRepository, denyLogRecorder);
    }

    private static DataOwnershipMigrationService.OwnershipMigrateRequest request() {
        return new DataOwnershipMigrationService.OwnershipMigrateRequest("wms.input", 9L, 1L, 42L);
    }

    /**
     * 资源表名 → 实体类 → 写侧仓储：三段接线都用替身，测试不依赖类路径扫描的结果
     *
     * @param repository 该实体对应的仓储替身（原样收下，用于断言"写到哪张表、写了哪几列"）
     */
    private void stubEntityClassIndex(ICurdRepository<?> repository) {
        given(entityClassIndex.byTableName("owned")).willReturn(OwnedEntity.class);
        given(entityRepositories.bindingOf(OwnedEntity.class))
                .willReturn(new EntityRepositories.Binding(repository, "test.OwnedRepository"));
        given(resourceRepository.getByResourceKey("wms.input"))
                .willReturn(new DataResourceEntity().setTableName("owned"));
    }

    private static PermissionContext context() {
        return new PermissionContext(7L, 1L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    private void grantOwnership() {
        UserEntity user = Mockito.mock(UserEntity.class);
        given(userService.get(7L)).willReturn(user);
        given(systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP)).willReturn(true);
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
            UserEntity user = Mockito.mock(UserEntity.class);
            given(userService.get(7L)).willReturn(user);
            given(systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_OWNERSHIP)).willReturn(false);

            assertThrows(RuntimeException.class, () -> service().migrate(request()));
            verify(denyLogRecorder, never()).record(any(), any(), any());
        }
    }

    @Test
    @DisplayName("持有 ownership 权限 → 只写归属两列 + 落审计")
    void ownerMigratesAndAudits() {
        stubEntityClassIndex(ownedRepository);
        OwnedEntity target = new OwnedEntity();
        target.setId(9L);
        target.setTenantId(2L);
        target.setCreatorId(3L);
        given(ownedRepository.selectById(9L)).willReturn(target);

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            grantOwnership();
            service().migrate(request());
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<CurdEntity<?>>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(ownedRepository).update(isNull(), captor.capture());
        UpdateWrapper<CurdEntity<?>> wrapper = captor.getValue();
        // 列名走 QueryHelper（字段名 → 列名），所以这里断言的正是"落到哪两列"
        assertTrue(wrapper.getSqlSegment().contains("tenant_id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getSqlSegment().contains("creator_id"), wrapper.getSqlSegment());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L), String.valueOf(wrapper.getParamNameValuePairs()));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(42L), String.valueOf(wrapper.getParamNameValuePairs()));
        assertEquals(9L, target.getId());
        assertEquals(2L, target.getTenantId(), "实体不被就地改写：写入交给只含归属列的 UPDATE 语句");
        verify(denyLogRecorder).record(eq("wms.input"), eq(DataPermissionAudit.REASON_OWNERSHIP_MIGRATED), any());
    }

    @Test
    @DisplayName("资源未登记 → 参数错误")
    void unregisteredResourceFails() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            grantOwnership();
            given(resourceRepository.getByResourceKey("wms.input")).willReturn(null);

            assertThrows(RuntimeException.class, () -> service().migrate(request()));
        }
    }

    @Test
    @DisplayName("记录不存在 → 参数错误（不落审计）")
    void missingRecordFails() {
        stubEntityClassIndex(ownedRepository);
        given(ownedRepository.selectById(9L)).willReturn(null);

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            grantOwnership();
            assertThrows(RuntimeException.class, () -> service().migrate(request()));
        }
        verify(denyLogRecorder, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("目标记录不是带归属实体 → 参数错误")
    void nonTenantAwareTargetFails() {
        // 资源表名反查出的实体类与实际读回的行类型不符：用一个不带归属的记录行模拟。
        // 仓储替身这里必须是原始类型 —— 否则 T 被钉成 OwnedEntity，塞不进"另一种行"。
        @SuppressWarnings({"unchecked", "rawtypes"})
        ICurdRepository rawRepository = Mockito.mock(ICurdRepository.class);
        stubEntityClassIndex(rawRepository);
        given(rawRepository.selectById(9L)).willReturn(Mockito.mock(CurdEntity.class));

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            grantOwnership();
            assertThrows(RuntimeException.class, () -> service().migrate(request()));
        }
        verify(denyLogRecorder, never()).record(any(), any(), any());
    }
}
