package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.personnel.role.RoleEntity;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>SystemPermissionService 权限标识比对测试</h1>
 *
 * <p>B-1 回归：框架按请求 URL 派生的权限标识实体名全小写（material_getPage），
 * 而权限登记用实体类名（Material_getPage）。库内唯一索引建在不区分大小写的排序规则上，
 * 所以 {@code getByIdentity} 查得到；内存比对若用区分大小写的 equals，
 * 凡走 URL 派生标识的调用方一律误判无权限——所有非 root 用户被 403。
 * 比对必须与库内口径一致：忽略大小写。</p>
 *
 * @author Hamm.cn
 */
class SystemPermissionServiceTest {

    private final SystemPermissionService service = new SystemPermissionService();

    private UserEntity userWithPermissions(String... identities) {
        RoleEntity role = new RoleEntity().setCode("auditor").setName("审计员");
        role.setId(3L);
        setField(role, "permissionList", identitySet(identities));
        UserEntity user = new UserEntity();
        user.setId(24L);
        setField(user, "roleList", new LinkedHashSet<>(Set.of(role)));
        return user;
    }

    private Set<PermissionEntity> identitySet(String... identities) {
        LinkedHashSet<PermissionEntity> set = new LinkedHashSet<>();
        for (String identity : identities) {
            set.add(new PermissionEntity().setIdentity(identity).setName(identity));
        }
        return set;
    }

    private void setField(Object target, String fieldName, Object value) {
        // isDisabled/permissionList 等字段声明在框架基类（CurdEntity）上，须沿父链找
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException(new NoSuchFieldException(fieldName));
    }

    @Test
    @DisplayName("登记的 PascalCase 权限必须能被 URL 派生的小写标识命中（B-1 核心回归）")
    void matchesUrlDerivedLowercaseAgainstPascalCaseIdentity() {
        UserEntity user = userWithPermissions("Material_getPage");
        // 拦截器从 /material/getPage 派生的是 material_getPage；库里登记的是 Material_getPage
        assertTrue(service.has(user, "material_getPage"),
                "大小写不同但同一权限：库内 CI 口径下必须判为持有，否则非 root 用户全部 403");
        assertTrue(service.has(user, "Material_getPage"), "精确大小写当然也要命中");
        assertTrue(service.has(user, "MATERIAL_GETPAGE"), "CI 口径下任意大小写组合都命中");
    }

    @Test
    @DisplayName("未登记的权限不命中")
    void deniesIdentityNotGranted() {
        UserEntity user = userWithPermissions("Material_getPage");
        assertFalse(service.has(user, "Material_delete"));
        assertFalse(service.has(user, "Contract_getPage"));
    }

    @Test
    @DisplayName("system 系列 colon 风格标识按精确口径命中")
    void matchesSystemColonStyleIdentity() {
        UserEntity user = userWithPermissions(SystemPermissionIdentities.DATA_PERMISSION_BYPASS);
        assertTrue(service.has(user, SystemPermissionIdentities.DATA_PERMISSION_BYPASS));
    }

    @Test
    @DisplayName("isRoot 通过 ROOT 标识判定")
    void isRootChecksRootIdentity() {
        assertTrue(service.isRoot(userWithPermissions(SystemPermissionIdentities.ROOT)));
        assertFalse(service.isRoot(userWithPermissions("Material_getPage")));
    }

    @Test
    @DisplayName("禁用角色立即失效：停用角色不得继续授权（B-7 回归）")
    void disabledRoleGrantsNothing() {
        RoleEntity role = new RoleEntity().setCode("auditor").setName("审计员");
        role.setId(3L);
        setField(role, "permissionList", identitySet("Material_getPage"));
        setField(role, "isDisabled", true);
        UserEntity user = new UserEntity();
        user.setId(24L);
        setField(user, "roleList", new LinkedHashSet<>(Set.of(role)));
        assertFalse(service.has(user, "material_getPage"),
                "角色已停用仍放行 = 「禁用角色」形同虚设，授权回收只剩删行一条路");
    }

    @Test
    @DisplayName("重新启用后授权恢复")
    void reEnabledRoleGrantsAgain() {
        RoleEntity role = new RoleEntity().setCode("auditor").setName("审计员");
        role.setId(3L);
        setField(role, "permissionList", identitySet("Material_getPage"));
        setField(role, "isDisabled", false);
        UserEntity user = new UserEntity();
        user.setId(24L);
        setField(user, "roleList", new LinkedHashSet<>(Set.of(role)));
        assertTrue(service.has(user, "material_getPage"));
    }

    @Test
    @DisplayName("is_disabled 为 null（历史行）视为启用，不误伤")
    void nullDisabledFlagTreatedAsEnabled() {
        RoleEntity role = new RoleEntity().setCode("legacy").setName("历史角色");
        role.setId(4L);
        setField(role, "permissionList", identitySet("Material_getPage"));
        // 不设 isDisabled：历史数据可能为 NULL，必须按启用处理
        UserEntity user = new UserEntity();
        user.setId(24L);
        setField(user, "roleList", new LinkedHashSet<>(Set.of(role)));
        assertTrue(service.has(user, "material_getPage"));
    }

    @Test
    @DisplayName("禁用的权限行同样不授权")
    void disabledPermissionGrantsNothing() {
        RoleEntity role = new RoleEntity().setCode("auditor").setName("审计员");
        role.setId(3L);
        PermissionEntity perm = new PermissionEntity().setIdentity("Material_getPage").setName("物料-分页查询");
        setField(perm, "isDisabled", true);
        setField(role, "permissionList", new LinkedHashSet<>(Set.of(perm)));
        UserEntity user = new UserEntity();
        user.setId(24L);
        setField(user, "roleList", new LinkedHashSet<>(Set.of(role)));
        assertFalse(service.has(user, "material_getPage"));
    }

    @Test
    @DisplayName("root 身份同口径：停用携带 system:root 的角色必须真实摘掉 root")
    void rootIdentityHonorsDisabledState() {
        RoleEntity rootRole = new RoleEntity().setCode("SYSTEM_ROOT").setName("系统超级管理员");
        rootRole.setId(999L);
        setField(rootRole, "permissionList", identitySet(SystemPermissionIdentities.ROOT));
        setField(rootRole, "isDisabled", true);
        UserEntity user = new UserEntity();
        user.setId(1L);
        setField(user, "roleList", new LinkedHashSet<>(Set.of(rootRole)));
        assertFalse(service.isRoot(user), "root 是所有校验的旁路开关，禁用不生效等于收不回超管");
    }

    @Test
    @DisplayName("UserEntity.isRootUser 同口径跳过禁用角色")
    void entityRootUserHonorsDisabledState() {
        RoleEntity rootRole = new RoleEntity().setCode("SYSTEM_ROOT").setName("系统超级管理员");
        rootRole.setId(999L);
        setField(rootRole, "permissionList", identitySet(SystemPermissionIdentities.ROOT));
        UserEntity enabled = new UserEntity();
        enabled.setId(1L);
        setField(enabled, "roleList", new LinkedHashSet<>(Set.of(rootRole)));
        assertTrue(enabled.isRootUser());
        setField(rootRole, "isDisabled", true);
        assertFalse(enabled.isRootUser(), "与 has() 同口径：停用即失效");
    }

    @Test
    @DisplayName("空入参一律判无权限，不抛异常")
    void nullInputsDenyWithoutThrowing() {
        assertFalse(service.has(null, "Material_getPage"));
        assertFalse(service.has(new UserEntity(), "Material_getPage"));
        assertFalse(service.has(userWithPermissions("Material_getPage"), null));
    }

    @Test
    @DisplayName("角色权限集合含 null 元素或空集合时不抛异常")
    void tolerantToDirtyCollections() {
        RoleEntity role = new RoleEntity().setCode("broken").setName("脏数据角色");
        role.setId(9L);
        setField(role, "permissionList", null);
        UserEntity user = new UserEntity();
        user.setId(1L);
        setField(user, "roleList", new LinkedHashSet<>(Set.of(role)));
        assertFalse(service.has(user, "Material_getPage"));
    }
}
