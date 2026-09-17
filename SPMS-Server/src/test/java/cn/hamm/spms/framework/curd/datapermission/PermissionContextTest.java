package cn.hamm.spms.framework.curd.datapermission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限上下文测试
 *
 * <p>重点是"部门范围"这一维加进来时<b>不能把老调用点全改一遍</b>：全仓三十多处 {@code new PermissionContext(...)}
 * 都在传 7 个参数，兼容性由这条测试钉住（改坏了会在编译期就炸，但"忘了保留兼容构造"这种写法本身
 * 需要有人拦，否则第一个重构的人会顺手把 30 多处都改了）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("PermissionContext：部门范围与兼容构造")
class PermissionContextTest {

    @Test
    @DisplayName("旧的 7 参构造照编译可用，且范围等于直属")
    void legacyConstructorKeepsScopeEqualToDirectDepartments() {
        PermissionContext context = new PermissionContext(
                1L, 1L, Set.of(10L, 11L), Set.of(), Set.of(), Set.of("R1"), false);

        assertEquals(Set.of(10L, 11L), context.departmentIds());
        assertEquals(context.departmentIds(), context.departmentScopeIds());
        assertTrue(context.coversDepartment(10L));
        assertFalse(context.coversDepartment(99L));
    }

    @Test
    @DisplayName("范围与直属可以不同：子部门在范围内但不算直属")
    void scopeCanWidenBeyondDirectDepartments() {
        PermissionContext context = new PermissionContext(
                1L, 1L, Set.of(10L), Set.of(10L, 20L, 21L), Set.of(), Set.of(), Set.of(), false);

        assertEquals(Set.of(10L), context.departmentIds());
        assertTrue(context.coversDepartment(21L), "父部门策略应当覆盖到子部门");
        // 直属集合仍然是"用户挂在哪个部门"的事实，不因为展开了就被改写（审计与前端对比都靠它）
        assertFalse(context.departmentIds().contains(21L));
    }

    @Test
    @DisplayName("范围为 null / 空时回落成直属：漏传只会少看数据，不会放开")
    void emptyScopeFallsBackToDirect() {
        PermissionContext nullScope = new PermissionContext(
                1L, 1L, Set.of(10L), null, Set.of(), Set.of(), Set.of(), false);
        assertEquals(Set.of(10L), nullScope.departmentScopeIds());
        assertTrue(nullScope.coversDepartment(10L));

        PermissionContext emptyScope = new PermissionContext(
                1L, 1L, Set.of(10L), Set.of(), Set.of(), Set.of(), Set.of(), false);
        assertEquals(Set.of(10L), emptyScope.departmentScopeIds());
    }

    @Test
    @DisplayName("null 部门 ID 永远不覆盖（部门维度策略没配主体 = 不匹配）")
    void nullDepartmentNeverCovered() {
        PermissionContext context = new PermissionContext(
                1L, 1L, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);

        assertFalse(context.coversDepartment(null));
    }

    @Test
    @DisplayName("集合都是不可变副本：上下文可能被多个线程读")
    void collectionsAreCopiedAndImmutable() {
        Set<Long> seeds = new LinkedHashSet<>();
        seeds.add(10L);
        PermissionContext context = new PermissionContext(
                1L, 1L, seeds, Set.of(), Set.of(), Set.of(), Set.of(), false);

        assertThrows(UnsupportedOperationException.class, () -> context.departmentIds().add(11L));
        // 外部改动不应回灌到已建好的上下文（Record 只做了拷贝，没共享引用）
        seeds.add(99L);
        assertEquals(Set.of(10L), context.departmentIds());
    }

    @Test
    @DisplayName("userId / tenantId 仍是必填")
    void requiredFields() {
        assertThrows(NullPointerException.class, () -> new PermissionContext(
                null, 1L, Set.of(), Set.of(), Set.of(), Set.of(), false));
        assertThrows(NullPointerException.class, () -> new PermissionContext(
                1L, null, Set.of(), Set.of(), Set.of(), Set.of(), false));
    }
}
