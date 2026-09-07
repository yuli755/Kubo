package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>主体策略与条件树合成器</h1>
 *
 * <p>一条策略的归属条件与 {@code condition_json} 必须先合成同一棵树，再交给 JPA 或 SQL
 * 渲染器。这里是两种读侧实现共用的唯一建树入口；任何一侧都不许重新按
 * {@code subject_type} 解释策略。</p>
 *
 * <p>{@code shouldBind} 也随树一起产出：它区分了「SYSTEM/ROLE 的无条件放行」和
 * 「本应生成归属条件但资源列绑定失败」。后者在 enforce 下只能恒假，不能退化成无条件查询。</p>
 *
 * @author Hamm.cn
 */
public final class PermissionRuleTreeBuilder {

    private final PermissionRuleParser ruleParser;

    public PermissionRuleTreeBuilder(PermissionRuleParser ruleParser) {
        this.ruleParser = ruleParser;
    }

    /**
     * 一条主体策略对应的合成结果
     *
     * @param rule       合成树；{@code null} 表示该策略没有任何附加条件
     * @param shouldBind 该策略本应生成归属条件
     */
    public record PolicyRule(PermissionRule rule, boolean shouldBind) {
    }

    /**
     * 合成策略列表；顺序保持提供者返回的优先级顺序。
     *
     * @param resource 资源登记
     * @param policies 当前主体可达的策略
     * @param context  当前主体上下文
     * @return 不可变策略树清单
     */
    public List<PolicyRule> build(DataResourceEntity resource,
                                   List<DataPolicyEntity> policies,
                                   PermissionContext context) {
        if (resource == null || policies == null || policies.isEmpty() || context == null) {
            return List.of();
        }
        List<PolicyRule> result = new ArrayList<>(policies.size());
        for (DataPolicyEntity policy : policies) {
            if (policy == null) {
                continue;
            }
            SubjectDimensionRules.SubjectRule subject = SubjectDimensionRules.forPolicy(resource, policy, context);
            PermissionRule condition = parseConditionRule(resource, policy);

            List<PermissionRule> parts = new ArrayList<>(2);
            if (subject.rule() != null && subject.rule().contributes()) {
                parts.add(subject.rule());
            }
            if (condition != null && condition.contributes()) {
                parts.add(condition);
            }
            PermissionRule combined = switch (parts.size()) {
                case 0 -> null;
                case 1 -> parts.get(0);
                default -> PermissionRule.group(PermissionRuleOp.ALL_OF, parts);
            };
            result.add(new PolicyRule(combined, subject.shouldBind()));
        }
        return List.copyOf(result);
    }

    private PermissionRule parseConditionRule(DataResourceEntity resource, DataPolicyEntity policy) {
        String json = policy.getConditionJson();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return null;
        }
        PermissionRule rule = ruleParser.parse(resource.getResourceKey(), json);
        if (!rule.contributes()) {
            return null;
        }
        new PermissionRuleValidator(allowListOf(resource)).validate(resource.getResourceKey(), rule);
        return rule;
    }

    private PermissionRuleValidator.FieldAllowList allowListOf(DataResourceEntity resource) {
        return PermissionRuleValidator.FieldAllowList.ofRegistrations(
                resource.getTenantColumn(), resource.getOwnerColumns(), resource.getFilterableColumns());
    }
}
