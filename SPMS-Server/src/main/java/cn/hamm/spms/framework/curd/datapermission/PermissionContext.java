package cn.hamm.spms.framework.curd.datapermission;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * <h1>请求级数据权限上下文</h1>
 *
 * <p>上下文只保存服务端认证后得到的主体信息，不接受前端直接提交的权限范围。</p>

 * <p><b>2026-09-18 分层搬迁</b>：本类型原在 {@code cn.hamm.spms.module.system.datapermission.context}，
 * 由<b>框架层</b>搬来 —— 它是纯共享内核（只依赖 JDK），却被 {@code base/BaseService}、
 * {@code base/TenantAwareEntity} 这些下层类使用，构成"下层依赖业务域"的反向依赖。
 * 权限的<b>实现</b>（策略存储、拦截器、审计落库、规则编译）仍留在
 * {@code module.system.datapermission}；这里只留<b>抽象与上下文</b>。</p>
 *
 * @author Hamm.cn
 */
public record PermissionContext(
        Long userId,
        Long tenantId,
        Set<Long> departmentIds,
        Set<Long> departmentScopeIds,
        Set<Long> factoryIds,
        Set<Long> warehouseIds,
        Set<String> roleCodes,
        boolean bypassDataPermission
) {

    /**
     * 兼容旧构造：未做层级展开时，"部门范围"就是直属部门本身
     *
     * <p>保留它是刻意的 —— 全仓 30 多处调用（含大量测试）不该因为加一个维度就被迫改，
     * 而"没展开"本来就该是默认状态（展开是<b>放宽</b>可见范围，必须显式开启）。</p>
     *
     * @param userId              用户 ID
     * @param tenantId            租户 ID
     * @param departmentIds       直属部门
     * @param factoryIds          工厂集合
     * @param warehouseIds        仓库集合
     * @param roleCodes           角色编码
     * @param bypassDataPermission 是否旁路
     */
    public PermissionContext(
            Long userId,
            Long tenantId,
            Set<Long> departmentIds,
            Set<Long> factoryIds,
            Set<Long> warehouseIds,
            Set<String> roleCodes,
            boolean bypassDataPermission
    ) {
        this(userId, tenantId, departmentIds, null, factoryIds, warehouseIds, roleCodes, bypassDataPermission);
    }

    public PermissionContext {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        departmentIds = immutableCopy(departmentIds);
        // 范围为空时回落到直属集合：漏传只会退化成"不展开"（少看数据），不会退化成"看全部"
        departmentScopeIds = departmentScopeIds == null || departmentScopeIds.isEmpty()
                ? departmentIds : immutableCopy(departmentScopeIds);
        factoryIds = immutableCopy(factoryIds);
        warehouseIds = immutableCopy(warehouseIds);
        roleCodes = roleCodes == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
    }

    /**
     * 该部门是否在<em>当前主体的数据范围</em>内
     *
     * <p>判据放在这里而不是各调用点，是因为它曾被抄过三遍（策略可达性、谓词门槛、测试替身）——
     * 三处各判一次，就会出现"策略被认为可达、谓词却拼不出来"这种自相矛盾。
     * 用的是<b>范围</b>（含子部门闭包）而不是直属集合；两者在未开启展开时相同。</p>
     *
     * @param departmentId 策略主体 ID
     * @return 在范围内返回 true
     */
    public boolean coversDepartment(Long departmentId) {
        return departmentId != null && departmentScopeIds.contains(departmentId);
    }

    private static Set<Long> immutableCopy(Set<Long> values) {
        return values == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
