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
 * <p>角色匹配规则：策略的 {@code roleCode} 与当前主体角色精确匹配，
 * {@code default} 作为通配符，在没有精确匹配时对所有角色生效。</p>
 *
 * @author Hamm.cn
 */
@Service
public class ColumnMaskingService {

    /**
     * 通配角色编码：对所有角色生效，优先级低于精确匹配
     */
    public static final String DEFAULT_ROLE_CODE = "default";

    private final DataResourceRepository resourceRepository;
    private final DataFieldPolicyRepository fieldPolicyRepository;

    public ColumnMaskingService(DataResourceRepository resourceRepository, DataFieldPolicyRepository fieldPolicyRepository) {
        this.resourceRepository = resourceRepository;
        this.fieldPolicyRepository = fieldPolicyRepository;
    }

    /**
     * 解析当前主体对该字段生效的脱敏策略
     *
     * <p>按「角色精确匹配优先、default 通配兜底」的顺序选择，
     * 同优先级下取 {@code priority} 最大的策略。</p>
     *
     * @param resourceKey 数据资源标识（如 channel.salePrice）
     * @param fieldName   字段名（如 price）
     * @param context     权限上下文（角色取自上下文，不信任前端提交）
     * @return 生效策略；无匹配时返回 {@code null}
     */
    public DataFieldPolicyEntity getEffectiveMaskPolicy(String resourceKey, String fieldName, PermissionContext context) {
        DataResourceEntity resource = resourceRepository.getByResourceKey(resourceKey);
        if (resource == null || resource.getId() == null) {
            return null;
        }

        List<DataFieldPolicyEntity> policies = fieldPolicyRepository
                .findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(resource.getId(), "QUERY");
        if (policies == null || policies.isEmpty()) {
            return null;
        }

        DataFieldPolicyEntity exact = null;
        DataFieldPolicyEntity fallback = null;
        for (DataFieldPolicyEntity policy : policies) {
            if (!fieldName.equals(policy.getFieldName())) {
                continue;
            }
            String roleCode = policy.getRoleCode();
            if (roleCode == null || DEFAULT_ROLE_CODE.equals(roleCode)) {
                // 未指定角色或 default 通配，作为兜底策略
                fallback = effective(policy, fallback);
                continue;
            }
            if (context != null && context.roleCodes() != null && context.roleCodes().contains(roleCode)) {
                exact = effective(policy, exact);
            }
        }
        return exact != null ? exact : fallback;
    }

    private DataFieldPolicyEntity effective(DataFieldPolicyEntity policy, DataFieldPolicyEntity current) {
        if (current == null) {
            return policy;
        }
        int policyPriority = policy.getPriority() == null ? 0 : policy.getPriority();
        int currentPriority = current.getPriority() == null ? 0 : current.getPriority();
        return policyPriority > currentPriority ? policy : current;
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
        DataFieldPolicyEntity policy = getEffectiveMaskPolicy(resourceKey, fieldName, context);
        if (policy == null) {
            return value;
        }
        if (Boolean.TRUE.equals(policy.getAllowPlaintext())) {
            return value;
        }
        return applyMask(policy.getMaskType(), policy.getMaskExpression(), value);
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