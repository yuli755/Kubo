package cn.hamm.spms.module.system.datapermission;

import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RowPermissionPredicateTest {
    @Test
    void createsParameterizedTenantPredicate() {
        RowPermissionPredicate predicate = RowPermissionPredicate.tenant("inventory", "tenant_id", 100L);

        assertEquals("inventory.tenant_id = :dataPermissionTenantId", predicate.sql());
        assertEquals(100L, predicate.parameters().get("dataPermissionTenantId"));
    }

    @Test
    void deniesWhenTenantIsMissing() {
        RowPermissionPredicate predicate = RowPermissionPredicate.tenant("inventory", "tenant_id", null);

        assertEquals("1 = 0", predicate.sql());
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> RowPermissionPredicate.tenant("inventory;drop", "tenant_id", 1L));
    }
}