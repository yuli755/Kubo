package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策略主体可达性判定测试
 *
 * <p>这个判定原本有三份副本（管理端服务、带缓存的运行时提供者、测试替身），三份对 null 的写法都不一样。
 * 收口成一份之后，本类就是它的<em>唯一</em>语义规格 —— 尤其是"未知类型一律不匹配"这条 fail-closed 底线。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataPermissionSubjectMatcher：主体可达性")
class DataPermissionSubjectMatcherTest {

    private static final PermissionContext CONTEXT = new PermissionContext(
            7L, 3L, Set.of(10L), Set.of(10L, 20L), Set.of(30L), Set.of(40L), Set.of("WAREHOUSE_OP"), false);

    private static DataPolicyEntity policy(String subjectType, Long subjectId) {
        DataPolicyEntity policy = new DataPolicyEntity();
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId);
        return policy;
    }

    @Test
    @DisplayName("SYSTEM 恒匹配，与主体无关")
    void systemAlwaysMatches() {
        assertTrue(DataPermissionSubjectMatcher.matches(policy("SYSTEM", null), CONTEXT));
    }

    @Test
    @DisplayName("USER / TENANT 按 ID 相等；subjectId 为空一律不匹配")
    void userAndTenant() {
        assertTrue(DataPermissionSubjectMatcher.matches(policy("USER", 7L), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("USER", 8L), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("USER", null), CONTEXT));
        assertTrue(DataPermissionSubjectMatcher.matches(policy("TENANT", 3L), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("TENANT", null), CONTEXT));
    }

    @Test
    @DisplayName("ROLE 看 roleCode，不看 subjectId")
    void roleUsesRoleCode() {
        assertTrue(DataPermissionSubjectMatcher.matches(policy("ROLE", null).setRoleCode("WAREHOUSE_OP"), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("ROLE", null).setRoleCode("OTHER"), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("ROLE", null), CONTEXT));
    }

    @Test
    @DisplayName("DEPARTMENT 用范围：子部门可达，直属之外也不算越界")
    void departmentUsesScopeNotDirectList() {
        assertTrue(DataPermissionSubjectMatcher.matches(policy("DEPARTMENT", 10L), CONTEXT));
        assertTrue(DataPermissionSubjectMatcher.matches(policy("DEPARTMENT", 20L), CONTEXT),
                "20 是 10 的子部门：策略配在父部门时应当覆盖子部门（开关打开后）");
        assertFalse(DataPermissionSubjectMatcher.matches(policy("DEPARTMENT", 21L), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("DEPARTMENT", null), CONTEXT));
    }

    @Test
    @DisplayName("未开启层级展开时（范围=直属），子部门策略自然不匹配")
    void withoutExpansionSubDepartmentPolicyDoesNotMatch() {
        PermissionContext noExpansion = new PermissionContext(
                7L, 3L, Set.of(10L), Set.of(), Set.of(), Set.of(), Set.of(), false);

        assertFalse(DataPermissionSubjectMatcher.matches(policy("DEPARTMENT", 20L), noExpansion));
        assertTrue(DataPermissionSubjectMatcher.matches(policy("DEPARTMENT", 10L), noExpansion));
    }

    @Test
    @DisplayName("FACTORY / WAREHOUSE 按各自集合")
    void factoryAndWarehouse() {
        assertTrue(DataPermissionSubjectMatcher.matches(policy("FACTORY", 30L), CONTEXT));
        assertTrue(DataPermissionSubjectMatcher.matches(policy("WAREHOUSE", 40L), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("WAREHOUSE", 30L), CONTEXT));
    }

    @Test
    @DisplayName("未知 / null 的 subjectType 不匹配（fail-closed，不猜语义）")
    void unknownSubjectTypeIsRejected() {
        assertFalse(DataPermissionSubjectMatcher.matches(policy("CUSTOM", 7L), CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy(null, 7L), CONTEXT));
    }

    @Test
    @DisplayName("policy 或 context 为 null：不匹配而不是 NPE")
    void nullSafe() {
        assertFalse(DataPermissionSubjectMatcher.matches(null, CONTEXT));
        assertFalse(DataPermissionSubjectMatcher.matches(policy("USER", 7L), null));
    }
}
