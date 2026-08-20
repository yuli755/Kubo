package cn.hamm.spms.module.system.datapermission.context;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * <h1>请求级数据权限上下文</h1>
 *
 * <p>上下文只保存服务端认证后得到的主体信息，不接受前端直接提交的权限范围。</p>
 *
 * @author Hamm.cn
 */
public record PermissionContext(
        Long userId,
        Long tenantId,
        Set<Long> departmentIds,
        Set<Long> factoryIds,
        Set<Long> warehouseIds,
        Set<String> roleCodes,
        boolean bypassDataPermission
) {
    public PermissionContext {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        departmentIds = immutableCopy(departmentIds);
        factoryIds = immutableCopy(factoryIds);
        warehouseIds = immutableCopy(warehouseIds);
        roleCodes = roleCodes == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
    }

    private static Set<Long> immutableCopy(Set<Long> values) {
        return values == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
