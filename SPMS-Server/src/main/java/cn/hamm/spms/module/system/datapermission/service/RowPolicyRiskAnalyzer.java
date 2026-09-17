package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <h1>行级策略"永远不会命中"的静态盘点</h1>
 *
 * <p>要解决的问题与列级那份同源，但代价更硬：行级规则一旦不命中，效果不是"少个提示"，
 * 而是<b>该主体在这个资源上的防护不存在</b>，界面上却显示"已配置、已启用"。
 * 三条判据全部来自运行期的取数与匹配代码，不是推测：</p>
 * <ul>
 *   <li><b>动作名不在 {@code DataPermissionAction} 里</b>：{@code DataPolicyRepository} 按
 *       {@code resourceId + actionName} 精确取数（动作名来自枚举 {@code name()}），
 *       写错的行任何请求都读不到；</li>
 *   <li><b>主体条件恒为 false</b>：{@code matchesSubject} 对未知 {@code subjectType} 走
 *       {@code default -> false}，ROLE 缺 roleCode、其它类型缺 subjectId 同样恒 false；</li>
 *   <li><b>维度尚未接入</b>：FACTORY 主体要匹配的 {@code context.factoryIds()} 恒为空集
 *       （见 {@code DimensionGuard} 与施工图 D13），所以这类行今天谁也匹配不上。</li>
 * </ul>
 *
 * <p><b>刻意不算风险的两件事</b>：</p>
 * <ol>
 *   <li>{@code condition_json} 为 {@code {}}（不产生任何条件）—— 这是<b>设计上的"允许全部"</b>
 *       （{@code DataPermissionSpecification} 的注释明确写了"SYSTEM / ROLE 策略语义是允许全部，
 *       生成不出条件是正确结果"）。把它报成风险就是把正常配置当缺陷，面板会因此被无视；</li>
 *   <li>{@code enabled=false} 的行 —— 那是关着的，不是坏了。</li>
 * </ol>
 *
 * <p>本类不查库、不判灰度模式，输入由调用方给（与 {@link FieldPolicyGapAnalyzer} 同一套做法）。</p>
 *
 * @author Hamm.cn
 */
public final class RowPolicyRiskAnalyzer {

    /**
     * 动作名不在枚举内 ⇒ 取数按动作精确匹配，这一行永远读不到
     */
    public static final String KIND_UNREACHABLE_ACTION = "UNREACHABLE_ACTION";

    /**
     * 主体条件恒不成立 ⇒ matchesSubject 永远返回 false
     */
    public static final String KIND_UNMATCHABLE_SUBJECT = "UNMATCHABLE_SUBJECT";

    /**
     * 主体类型依赖的 id 集合在当前实现里恒空（FACTORY / D13 未落地）
     */
    public static final String KIND_DIMENSION_NOT_WIRED = "DIMENSION_NOT_WIRED";

    /**
     * 写侧 CREATE 动作配了显式恒假（{@code {"deny":true}}）：动作级只看「有策略」、行级对新建直接放行，
     * 这条「拒绝」永远不会生效
     */
    public static final String KIND_EXPLICIT_DENY = "EXPLICIT_DENY";

    /**
     * 条件树解析器（无状态、线程安全）。只为盘出「显式恒假」这一类存在；
     * 解析失败的策略不在这里重复报 —— 那归运行期 fail-closed 的 {@code condition-parse-failed} 口径。
     */
    private static final PermissionRuleParser PARSER = new PermissionRuleParser();

    /**
     * 一条风险
     *
     * @param policyId   策略行主键
     * @param policyCode 策略编码（人认这个，不认 ID）
     * @param kind       风险类别常量
     * @param detail     人话说明（直接给管理端渲染，别在前端重算语义）
     */
    public record Risk(Long policyId, String policyCode, String kind, String detail) {
    }

    private RowPolicyRiskAnalyzer() {
    }

    /**
     * @param enabledPolicies    该资源全部启用中的行级策略（跨动作，含动作写错的那些）
     * @param factoryDimensionWired 工厂维度是否已接入；未接入时 FACTORY 主体按"恒不命中"报出
     * @return 风险清单；每条策略最多一条（按 动作 → 主体 → 维度 的优先级取第一个成立的原因）
     */
    public static List<Risk> risks(List<DataPolicyEntity> enabledPolicies, boolean factoryDimensionWired) {
        if (enabledPolicies == null || enabledPolicies.isEmpty()) {
            return List.of();
        }
        Set<String> actions = enumNames(DataPermissionAction.values());
        Set<String> subjectTypes = enumNames(DataPermissionSubjectType.values());

        List<Risk> risks = new ArrayList<>();
        for (DataPolicyEntity policy : enabledPolicies) {
            String action = trim(policy.getActionName());
            if (action.isEmpty() || !actions.contains(action)) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), KIND_UNREACHABLE_ACTION,
                        "动作「" + (action.isEmpty() ? "(未填)" : action) + "」不是合法的数据权限动作：取数按 资源 + 动作 精确匹配，"
                                + "这一行任何请求都不会读到。若它是该主体在此资源上的唯一策略，enforce 后会落到默认拒绝（该主体查不到数据）。"));
                continue;
            }

            if (DataPermissionAction.CREATE.name().equals(action) && isExplicitDeny(policy.getConditionJson())) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), KIND_EXPLICIT_DENY,
                        "CREATE 策略的条件是显式恒假（condition_json = {\"deny\":true}）：写侧动作级只认「有策略即允许」、"
                                + "行级守卫对新建直接放行（见 RowWriteGuard 的 CREATE 边界），这条「拒绝」不会生效 —— 创建照常放行。"
                                + "白名单模型下「禁止创建」的正确表达是<b>删除这条 CREATE 策略</b>（无策略即拒绝），而不是配恒假条件。"));
                continue;
            }

            String type = trim(policy.getSubjectType());
            if (type.isEmpty() || !subjectTypes.contains(type)) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), KIND_UNMATCHABLE_SUBJECT,
                        "主体类型「" + (type.isEmpty() ? "(未填)" : type) + "」不在 DataPermissionSubjectType 里，"
                                + "主体匹配走 default 分支恒为 false ⇒ 这一行谁也匹配不上。"));
                continue;
            }

            if (DataPermissionSubjectType.FACTORY.name().equals(type) && !factoryDimensionWired) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), KIND_DIMENSION_NOT_WIRED,
                        "FACTORY 主体要匹配的 factoryIds 在当前实现里恒为空集（没有 user_factory 关系表，见施工图 D13）"
                                + " ⇒ 谁都匹配不上；写入侧的 DimensionGuard 也已拒绝新增此类策略。"));
                continue;
            }

            if (neverHasSubject(type, policy)) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), KIND_UNMATCHABLE_SUBJECT,
                        "主体类型 " + type + " 需要 " + requiredFieldOf(type) + "，但它为空 ⇒ 恒不命中。"));
            }
        }
        return risks;
    }

    /**
     * "这个主体类型永远拿不到它需要的值"——与 {@code DataPermissionService.matchesSubject} 一一对应
     */
    private static boolean neverHasSubject(String type, DataPolicyEntity policy) {
        if (DataPermissionSubjectType.SYSTEM.name().equals(type)) {
            // SYSTEM 恒匹配，不需要任何 id
            return false;
        }
        if (DataPermissionSubjectType.ROLE.name().equals(type)) {
            return trim(policy.getRoleCode()).isEmpty();
        }
        return policy.getSubjectId() == null;
    }

    /**
     * 条件树是不是「显式恒假」（{@code {"deny":true}} → {@link PermissionRule.Deny}）
     *
     * <p>解析失败（非法 JSON / 裸 SQL / 非法形状）一律返回 false：那类策略的运行期表现是
     * fail-closed 的「条件解析失败」，不该被这里当成「显式拒绝」重复上报。</p>
     */
    private static boolean isExplicitDeny(String conditionJson) {
        try {
            return PARSER.parse("policy", conditionJson) instanceof PermissionRule.Deny;
        }
        catch (RuntimeException ex) {
            return false;
        }
    }

    private static String requiredFieldOf(String type) {
        if (DataPermissionSubjectType.ROLE.name().equals(type)) {
            return "roleCode";
        }
        return "subjectId";
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
