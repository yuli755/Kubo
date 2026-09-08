package cn.hamm.spms.base;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>归属列写入语义的用例（D1 写侧兑现）</h1>
 *
 * <p>{@code fillAttribution} 是 {@code @PrePersist} 回调，这里直接调用同一方法体断言：
 * 新建时归属<b>无条件</b>用服务端上下文覆盖 —— 「客户端没填才补」的旧实现会让请求方
 * 提交任意 {@code tenant_id}/{@code creator_id} 伪造归属（把数据建进/送出自己视野）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("TenantAwareEntity · 归属列写入")
class TenantAwareEntityTest {

    private static PermissionContext context() {
        // (userId, tenantId, departmentIds, factoryIds, warehouseIds, roleCodes, bypass)
        return new PermissionContext(42L, 7L, Set.of(1L), Set.of(2L), Set.of(3L), Set.of("ADMIN"), false);
    }

    @Test
    @DisplayName("新建：客户端提交的 tenant/creator 被服务端上下文无条件覆盖")
    void createOverridesSubmittedAttribution() {
        TenantAwareEntity<?> entity = new TenantAwareEntity<>();
        entity.setTenantId(999L);
        entity.setCreatorId(888L);

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            entity.fillAttribution();
        }

        assertEquals(7L, entity.getTenantId());
        assertEquals(42L, entity.getCreatorId());
    }

    @Test
    @DisplayName("新建：客户端没传时同样由服务端上下文补上")
    void createFillsEmptyAttribution() {
        TenantAwareEntity<?> entity = new TenantAwareEntity<>();

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            entity.fillAttribution();
        }

        assertEquals(7L, entity.getTenantId());
        assertEquals(42L, entity.getCreatorId());
    }

    @Test
    @DisplayName("无上下文（定时任务/迁移）：保持字段现值，不猜租户")
    void noContextKeepsCurrentValues() {
        PermissionContextHolder.clear();
        TenantAwareEntity<?> entity = new TenantAwareEntity<>();
        entity.setTenantId(1L);
        entity.setCreatorId(2L);

        entity.fillAttribution();

        assertEquals(1L, entity.getTenantId());
        assertEquals(2L, entity.getCreatorId());
    }

    @Test
    @DisplayName("无上下文且未设值：保持 null，由数据库默认落平台租户")
    void noContextLeavesFieldsUntouched() {
        PermissionContextHolder.clear();
        TenantAwareEntity<?> entity = new TenantAwareEntity<>();

        entity.fillAttribution();

        assertNull(entity.getTenantId());
        assertNull(entity.getCreatorId());
    }
}
