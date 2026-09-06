package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * <h1>列级脱敏服务</h1>
 *
 * <p>基于 {@code data_field_policy} 表配置的脱敏规则，对字段值进行脱敏处理。</p>
 *
 * <p>支持的脱敏策略（与 {@code packages/shared/src/utils/mask.ts} 的
 * {@code applyMaskPolicy} 一一对应，前端有逐分支对账测试；改这里请同步改那边）：</p>
 * <ul>
 *   <li>{@code FULL} — 完全遮蔽（返回表达式，无表达式时 {@code ****}）</li>
 *   <li>{@code PARTIAL} — 部分遮蔽（保留首尾各 1 位，中间星号封顶 6 个）</li>
 *   <li>{@code REPLACE} — 替换为指定表达式</li>
 *   <li>{@code LAST4} — 只保留末四位（长度不足整体遮蔽）</li>
 *   <li>{@code HASH} — 不可逆摘要 {@code h:} + SHA-256 前 12 位（同值同码，可比对不可还原）</li>
 *   <li>其它 / 未配置 — 安全失败为 {@code ****}，绝不回退原值</li>
 * </ul>
 *
 * <p><b>策略解析走 {@link DataFieldPolicyProvider}</b>：本服务被
 * {@code SensitiveFieldSerializer} 在每一行、每个敏感字段上调用，
 * 直连 Repository 会让一千行 × 三个字段的列表接口打出数千条 SQL。
 * Provider 的缓存键只有 {@code resourceKey + actionName}，角色命中与优先级选择
 * 留在本方法内存里做 —— 这样缓存内容与用户无关，不存在"串号"空间。</p>
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

    /**
     * 列级脱敏只解析 {@code QUERY} 动作的策略（序列化出口都是查询语义）；
     * 导出等其它出口若要复用策略，需显式传入对应 actionName，见 {@link #resolvePolicies}
     */
    public static final String DEFAULT_ACTION = "QUERY";

    private final DataFieldPolicyProvider fieldPolicyProvider;

    public ColumnMaskingService(DataFieldPolicyProvider fieldPolicyProvider) {
        this.fieldPolicyProvider = fieldPolicyProvider;
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
        return getEffectiveMaskPolicy(resourceKey, fieldName, DEFAULT_ACTION, context);
    }

    /**
     * 指定动作的策略解析（导出等其它出口复用同一套规则时走这里）
     *
     * @param resourceKey 数据资源标识
     * @param fieldName   字段名
     * @param actionName  动作名（QUERY / EXPORT 等，与 {@code data_resource.supported_actions} 对齐）
     * @param context     权限上下文
     * @return 生效策略；无匹配时返回 {@code null}
     */
    public DataFieldPolicyEntity getEffectiveMaskPolicy(
            String resourceKey, String fieldName, String actionName, PermissionContext context) {
        List<DataFieldPolicyEntity> policies = resolvePolicies(resourceKey, actionName);
        if (policies.isEmpty()) {
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

    /**
     * 取资源在该动作下的全部启用策略（带缓存，身份无关）
     */
    private List<DataFieldPolicyEntity> resolvePolicies(String resourceKey, String actionName) {
        List<DataFieldPolicyEntity> policies = fieldPolicyProvider
                .getEnabledPoliciesByResourceAndAction(resourceKey, actionName);
        return policies != null ? policies : java.util.Collections.emptyList();
    }

    /**
     * 按资源解析当前主体在 {@code QUERY} 动作下、每个字段的生效策略
     *
     * <p>给前端的 {@code my-mask-map} 用：编辑表单需要知道"哪些字段是受限的"，
     * 才能给出准确提示、并对数值字段提前中止提交（"把受限字段从提交体里删掉"是错的做法：
     * 整实体更新语义下那等于把该列写成 null）。逐字段查策略会让一个 8 字段的表单发 8 个请求，
     * 而表单恰恰是最需要这份信息的地方。</p>
     *
     * @param resourceKey 数据资源标识
     * @param context     权限上下文
     * @return 字段名 → 生效策略；资源未注册或无策略时返回空 Map
     */
    public Map<String, DataFieldPolicyEntity> getEffectiveMaskPolicyMap(String resourceKey, PermissionContext context) {
        List<DataFieldPolicyEntity> policies = resolvePolicies(resourceKey, DEFAULT_ACTION);
        if (policies.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, DataFieldPolicyEntity> result = new java.util.LinkedHashMap<>();
        for (DataFieldPolicyEntity policy : policies) {
            String fieldName = policy.getFieldName();
            if (fieldName == null || result.containsKey(fieldName)) {
                continue;
            }
            DataFieldPolicyEntity effective = getEffectiveMaskPolicy(resourceKey, fieldName, context);
            if (effective != null) {
                result.put(fieldName, effective);
            }
        }
        return result;
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
        if (context == null) {
            // 有策略但身份未解析：安全失败（遮蔽）而不是原样输出。
            // 系统内部调用应显式构造带 bypass 的上下文，而不是依赖此处放行。
            return defaultMask(policy);
        }
        if (Boolean.TRUE.equals(policy.getAllowPlaintext())) {
            return value;
        }
        return applyMask(policy.getMaskType(), policy.getMaskExpression(), value);
    }

    /**
     * 根据脱敏类型应用脱敏
     *
     * <p>遮蔽结果一律是<b>字符串</b>：被标记字段在本项目里多为 {@code Double price}
     * 这类数值字段，序列化层必须以字符串写出（{@code SensitiveFieldSerializer} 负责），
     * 切勿把结果回写进实体字段。</p>
     *
     * <p>口径与前端 {@code packages/shared/src/utils/mask.ts} 对齐：
     * LAST4 只留末四位（数值字段等价于全遮蔽），HASH 输出不可逆摘要前缀
     * （"需要比对但不能看原值"的场景），未知类型按最严格处理。</p>
     */
    private Object applyMask(String maskType, String maskExpression, Object value) {
        if (value == null) {
            return null;
        }
        String strValue = value.toString();

        return switch (maskType != null ? maskType.toUpperCase() : "FULL") {
            case "FULL" -> maskExpression != null ? maskExpression : "****";
            case "REPLACE" -> maskExpression != null ? maskExpression : "****";
            case "PARTIAL" -> maskPartial(strValue);
            case "LAST4" -> maskLast4(strValue, maskExpression);
            case "HASH" -> digest(strValue);
            // 未配置或配置了未知类型：安全失败，绝不回退原值
            default -> "****";
        };
    }

    /**
     * 只保留末四位；长度不足时整体遮蔽
     */
    private String maskLast4(String value, String maskExpression) {
        if (value.length() <= 4) {
            return maskExpression != null ? maskExpression : "****";
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    /**
     * 不可逆摘要（SHA-256 前 12 位十六进制）：同值同码可比对、不可还原
     */
    private String digest(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
                if (hex.length() >= 12) {
                    break;
                }
            }
            return "h:" + hex.substring(0, Math.min(12, hex.length()));
        }
        catch (java.security.NoSuchAlgorithmException ex) {
            return "****";
        }
    }

    /**
     * 身份缺失时使用的兜底遮蔽值
     */
    private Object defaultMask(DataFieldPolicyEntity policy) {
        String expression = policy.getMaskExpression();
        return expression != null && !expression.isBlank() ? expression : "****";
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