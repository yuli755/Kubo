package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <h1>列级脱敏服务</h1>
 *
 * <p>基于 {@code data_field_policy} 表配置的脱敏规则，对字段值进行脱敏处理。</p>
 *
 * <p>支持的脱敏策略：</p>
 * <ul>
 *   <li>{@code FULL} — 完全遮蔽（返回固定星号）</li>
 *   <li>{@code PARTIAL} — 部分遮蔽（保留首尾字符，中间用星号替代）</li>
 *   <li>{@code REPLACE} — 替换为指定表达式</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@Service
public class ColumnMaskingService {

    private final DataResourceRepository resourceRepository;
    private final DataFieldPolicyRepository fieldPolicyRepository;

    public ColumnMaskingService(DataResourceRepository resourceRepository, DataFieldPolicyRepository fieldPolicyRepository) {
        this.resourceRepository = resourceRepository;
        this.fieldPolicyRepository = fieldPolicyRepository;
    }

    /**
     * 对字段值进行脱敏
     *
     * @param resourceKey 数据资源标识
     * @param fieldName   字段名
     * @param value       原始值
     * @param context     权限上下文
     * @return 脱敏后的值（若无需脱敏则返回原值）
     */
    public Object maskFieldValue(String resourceKey, String fieldName, Object value, PermissionContext context) {
        DataResourceEntity resource = resourceRepository.getByResourceKey(resourceKey);
        if (resource == null || resource.getId() == null) {
            return value;
        }

        List<DataFieldPolicyEntity> policies = fieldPolicyRepository
                .findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(resource.getId(), "QUERY");

        if (policies.isEmpty()) {
            return value;
        }

        for (DataFieldPolicyEntity policy : policies) {
            if (!fieldName.equals(policy.getFieldName())) {
                continue;
            }
            // 检查角色是否匹配
            if (policy.getRoleCode() != null && !context.roleCodes().contains(policy.getRoleCode())) {
                continue;
            }
            // 如果允许原值，跳过脱敏
            if (Boolean.TRUE.equals(policy.getAllowPlaintext())) {
                return value;
            }
            return applyMask(policy.getMaskType(), policy.getMaskExpression(), value);
        }

        return value;
    }

    /**
     * 根据脱敏类型应用脱敏
     */
    private Object applyMask(String maskType, String maskExpression, Object value) {
        if (value == null) {
            return null;
        }
        String strValue = value.toString();

        return switch (maskType != null ? maskType.toUpperCase() : "FULL") {
            case "FULL" -> "****";
            case "REPLACE" -> maskExpression != null ? maskExpression : "****";
            case "PARTIAL" -> maskPartial(strValue);
            default -> "****";
        };
    }

    /**
     * 部分遮蔽：保留首尾各1个字符，中间用星号替代
     */
    private String maskPartial(String value) {
        if (value.length() <= 2) {
            return "**";
        }
        return value.charAt(0) + "*".repeat(Math.min(value.length() - 2, 6)) + value.charAt(value.length() - 1);
    }
}