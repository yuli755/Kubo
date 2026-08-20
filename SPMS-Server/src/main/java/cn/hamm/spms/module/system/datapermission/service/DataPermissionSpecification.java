package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>数据权限 JPA Specification 构建器</h1>
 *
 * <p>基于当前请求的 {@link PermissionContext} 和资源标识，生成 JPA Criteria API 的 {@link Predicate} 列表，
 * 供业务 Service 的 {@code addSearchPredicate} 方法叠加使用。</p>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 *   List<Predicate> dataPredicates = dataPermissionSpecification.buildPredicates(
 *       root, builder, "wms.inventory", DataPermissionAction.QUERY
 *   );
 *   predicateList.addAll(dataPredicates);
 * }</pre>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionSpecification {

    private final DataPermissionService dataPermissionService;

    public DataPermissionSpecification(DataPermissionService dataPermissionService) {
        this.dataPermissionService = dataPermissionService;
    }

    /**
     * 为指定资源构建数据权限过滤条件
     *
     * @param root        JPA Root
     * @param builder     JPA CriteriaBuilder
     * @param resourceKey 数据资源标识（如 {@code wms.inventory}）
     * @param action      数据操作类型
     * @param <T>         实体类型
     * @return 权限过滤谓词列表（空列表表示无限制）
     */
    public <T> List<Predicate> buildPredicates(
            Root<T> root,
            CriteriaBuilder builder,
            String resourceKey,
            DataPermissionAction action
    ) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            return List.of();
        }

        // 检查资源是否已注册
        DataResourceEntity resource = null;
        try {
            resource = dataPermissionService.requireResource(resourceKey);
        } catch (Exception ignored) {
            // 资源未注册时跳过权限控制（向后兼容，不影响现有业务）
            return List.of();
        }

        List<DataPolicyEntity> policies = dataPermissionService.getPolicies(resourceKey, action, context);

        if (policies.isEmpty() && dataPermissionService.shouldDenyByDefault(resourceKey, context)) {
            // 默认拒绝：无策略 → 返回空结果
            return List.of(builder.disjunction());
        }

        return buildPredicatesFromPolicies(root, builder, resource, policies, context);
    }

    /**
     * 从策略列表构建 JPA 谓词
     */
    private <T> List<Predicate> buildPredicatesFromPolicies(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            List<DataPolicyEntity> policies,
            PermissionContext context
    ) {
        List<Predicate> predicates = new ArrayList<>();

        for (DataPolicyEntity policy : policies) {
            Predicate predicate = buildSinglePredicate(root, builder, resource, policy, context);
            if (predicate != null) {
                predicates.add(predicate);
            }
        }

        return predicates;
    }

    /**
     * 根据单个策略构建 JPA 谓词
     */
    private <T> Predicate buildSinglePredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        String subjectType = policy.getSubjectType();
        if (subjectType == null) {
            return null;
        }

        return switch (subjectType) {
            case "TENANT" -> buildTenantPredicate(root, builder, resource, context);
            case "USER" -> buildOwnerPredicate(root, builder, resource, context);
            case "DEPARTMENT" -> buildDepartmentPredicate(root, builder, resource, policy, context);
            case "FACTORY" -> buildFactoryPredicate(root, builder, resource, policy, context);
            case "WAREHOUSE" -> buildWarehousePredicate(root, builder, resource, policy, context);
            case "ROLE" -> null; // ROLE 策略通过 getPolicies 已过滤，无需额外 SQL 条件
            case "SYSTEM" -> null; // SYSTEM 策略允许所有，无需额外 SQL 条件
            default -> null;
        };
    }

    /**
     * 构建租户隔离谓词
     */
    private <T> Predicate buildTenantPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            PermissionContext context
    ) {
        String tenantColumn = resource.getTenantColumn();
        if (tenantColumn == null || tenantColumn.isBlank()) {
            return null;
        }
        try {
            return builder.equal(root.get(tenantColumn), context.tenantId());
        } catch (IllegalArgumentException ignored) {
            // 实体没有该字段时跳过
            return null;
        }
    }

    /**
     * 构建用户归属谓词
     */
    private <T> Predicate buildOwnerPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            PermissionContext context
    ) {
        String ownerColumns = resource.getOwnerColumns();
        if (ownerColumns == null || ownerColumns.isBlank()) {
            return null;
        }
        // ownerColumns 可以包含多个字段，用逗号分隔
        String[] columns = ownerColumns.split(",");
        List<Predicate> ownerPredicates = new ArrayList<>();
        for (String column : columns) {
            String trimmed = column.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ownerPredicates.add(builder.equal(root.get(trimmed), context.userId()));
            } catch (IllegalArgumentException ignored) {
                // 实体没有该字段时跳过
            }
        }
        if (ownerPredicates.isEmpty()) {
            return null;
        }
        if (ownerPredicates.size() == 1) {
            return ownerPredicates.get(0);
        }
        return builder.or(ownerPredicates.toArray(new Predicate[0]));
    }

    /**
     * 构建部门过滤谓词
     */
    private <T> Predicate buildDepartmentPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        if (policy.getSubjectId() == null || !context.departmentIds().contains(policy.getSubjectId())) {
            return null;
        }
        String departmentColumn = resolveDepartmentColumn(resource);
        if (departmentColumn == null) {
            return null;
        }
        try {
            return builder.equal(root.get(departmentColumn), policy.getSubjectId());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 构建工厂过滤谓词
     */
    private <T> Predicate buildFactoryPredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        if (policy.getSubjectId() == null || !context.factoryIds().contains(policy.getSubjectId())) {
            return null;
        }
        try {
            return builder.equal(root.get("factoryId"), policy.getSubjectId());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 构建仓库过滤谓词
     */
    private <T> Predicate buildWarehousePredicate(
            Root<T> root,
            CriteriaBuilder builder,
            DataResourceEntity resource,
            DataPolicyEntity policy,
            PermissionContext context
    ) {
        if (policy.getSubjectId() == null || !context.warehouseIds().contains(policy.getSubjectId())) {
            return null;
        }
        try {
            return builder.equal(root.get("warehouseId"), policy.getSubjectId());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 从资源定义中解析部门字段名
     */
    private String resolveDepartmentColumn(DataResourceEntity resource) {
        String ownerColumns = resource.getOwnerColumns();
        if (ownerColumns == null || ownerColumns.isBlank()) {
            return "departmentId";
        }
        return ownerColumns.split(",")[0].trim();
    }
}