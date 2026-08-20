package cn.hamm.spms.module.system.datapermission.service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <h1>行级权限 SQL 谓词</h1>
 *
 * <p>只生成带参数占位符的谓词，不把用户输入直接拼接到 SQL 值中。</p>
 *
 * @author Hamm.cn
 */
public record RowPermissionPredicate(String sql, Map<String, Object> parameters) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public RowPermissionPredicate {
        parameters = Map.copyOf(parameters);
    }

    public static RowPermissionPredicate tenant(String tableAlias, String tenantColumn, Long tenantId) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(tenantColumn, "tenantColumn");
        if (tenantId == null) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + tenantColumn + " = :dataPermissionTenantId",
                Map.of("dataPermissionTenantId", tenantId)
        );
    }

    public static RowPermissionPredicate owner(String tableAlias, String ownerColumn, Long userId) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(ownerColumn, "ownerColumn");
        if (userId == null) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + ownerColumn + " = :dataPermissionOwnerId",
                Map.of("dataPermissionOwnerId", userId)
        );
    }

    public static RowPermissionPredicate department(String tableAlias, String departmentColumn, Set<Long> departmentIds) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(departmentColumn, "departmentColumn");
        if (departmentIds == null || departmentIds.isEmpty()) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + departmentColumn + " IN (:dataPermissionDepartmentIds)",
                Map.of("dataPermissionDepartmentIds", departmentIds)
        );
    }

    public static RowPermissionPredicate factory(String tableAlias, String factoryColumn, Set<Long> factoryIds) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(factoryColumn, "factoryColumn");
        if (factoryIds == null || factoryIds.isEmpty()) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + factoryColumn + " IN (:dataPermissionFactoryIds)",
                Map.of("dataPermissionFactoryIds", factoryIds)
        );
    }

    public static RowPermissionPredicate warehouse(String tableAlias, String warehouseColumn, Set<Long> warehouseIds) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(warehouseColumn, "warehouseColumn");
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + warehouseColumn + " IN (:dataPermissionWarehouseIds)",
                Map.of("dataPermissionWarehouseIds", warehouseIds)
        );
    }

    public static RowPermissionPredicate deny() {
        return new RowPermissionPredicate("1 = 0", Map.of());
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " contains an unsafe SQL identifier");
        }
    }
}
