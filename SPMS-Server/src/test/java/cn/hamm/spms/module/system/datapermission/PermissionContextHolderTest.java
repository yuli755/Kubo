package cn.hamm.spms.module.system.datapermission;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionContextHolderTest {
    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    @Test
    void restoresPreviousContextAfterScopeCloses() {
        PermissionContext outer = context(10L, false);
        PermissionContext inner = context(20L, true);

        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(outer)) {
            assertEquals(10L, PermissionContextHolder.require().userId());
            try (PermissionContextHolder.Scope ignoredInner = PermissionContextHolder.open(inner)) {
                assertEquals(20L, PermissionContextHolder.require().userId());
                assertTrue(PermissionContextHolder.require().bypassDataPermission());
            }
            assertEquals(10L, PermissionContextHolder.require().userId());
            assertFalse(PermissionContextHolder.require().bypassDataPermission());
        }
        assertTrue(PermissionContextHolder.current().isEmpty());
    }

    private PermissionContext context(long userId, boolean bypass) {
        return new PermissionContext(userId, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), bypass);
    }
}