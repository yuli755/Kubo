package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h1>列级策略的"导出明文授权"缺口分析</h1>
 *
 * <p>要解决的具体问题：{@link ExportBypassGuard} 的规则是"被遮蔽的字段<b>每一个</b>都要有
 * {@code actionName=EXPORT} 且 {@code allowPlaintext=true} 的策略，缺一条就整份导出被拒"。
 * 于是最常见的生产事故形状是：<b>只配了 QUERY 遮蔽、忘了配 EXPORT 授权</b> —— 管理员在策略表里
 * 看见"配好了"，用户点导出撞一句 403。本类把这种组合缺口变成一眼可见的清单。</p>
 *
 * <p><b>刻意不做的事</b>：不查库、不判灰度模式、不预测"这次请求会不会被拒"。
 * 真正的判据在 {@link ExportColumnMasker#grantsPlaintextExport}（由
 * {@link ColumnMaskingService} 的角色解析支撑），这里只做<b>组合完整性</b>的静态盘点，
 * 角色覆盖规则与它保持一致（精确角色优先、{@code default} 兜底）。
 * 把 enforce/shadow 的判断也搬进来，就等于第二套真相 —— 那正是本项目反复在修的东西。</p>
 *
 * @author Hamm.cn
 */
public final class FieldPolicyGapAnalyzer {

    /**
     * 一处缺口：某个角色在某个字段上被遮蔽，却没有对应的导出明文授权
     *
     * @param fieldName 被遮蔽的字段名
     * @param roleCode  生效的角色（{@code default} = 对所有角色兜底）
     * @param reason    人话说明（直接给管理端渲染，不再在前端重算语义）
     */
    public record Gap(String fieldName, String roleCode, String reason) {
    }

    /**
     * 一条动作不会被列级遮蔽读到的启用策略
     *
     * @param id         策略行主键（供管理端定位；盘点只报告，不代替人删）
     * @param fieldName  字段名
     * @param roleCode   角色编码
     * @param actionName 动作名（原样回显，含拼错的值；未填时给出占位）
     */
    public record Inert(Long id, String fieldName, String roleCode, String actionName) {
    }

    private FieldPolicyGapAnalyzer() {
    }

    /**
     * 找出"动作不在消费集里"的启用策略
     *
     * <p>为什么值得单独盘一次：{@code action_name} 是字符串存的，后端不会因为选了 {@code DETAIL}
     * 或写成 {@code REPROT} 而报错 —— 于是这行策略保存成功、列表里看得见、编辑时一切正常，
     * 唯一的效果是"界面上什么都没发生"。<b>"配了却没人读"比报错更难发现，因为它不产生失败信号</b>，
     * 而管理员对策略表的信任就建立在这种一次次的"看起来配好了"上。</p>
     *
     * @param enabledPolicies 该资源全部启用中的策略（跨动作）
     * @param consumedActions 列级遮蔽真正消费的动作名（由调用方从后端常量给出，别在这里抄一份）
     * @return 不在消费集里的策略行；消费集为空时返回空 —— 配置缺失时宁可不报，也不误伤
     */
    public static List<Inert> inertPolicies(List<DataFieldPolicyEntity> enabledPolicies, Set<String> consumedActions) {
        if (enabledPolicies == null || enabledPolicies.isEmpty()
                || consumedActions == null || consumedActions.isEmpty()) {
            return List.of();
        }
        List<Inert> inert = new ArrayList<>();
        for (DataFieldPolicyEntity policy : enabledPolicies) {
            String action = policy.getActionName() == null ? "" : policy.getActionName().trim();
            if (!action.isEmpty() && consumedActions.contains(action)) {
                continue;
            }
            inert.add(new Inert(policy.getId(), policy.getFieldName(), policy.getRoleCode(),
                    action.isEmpty() ? "(未填)" : action));
        }
        return inert;
    }

    /**
     * @param maskingPolicies 该资源 QUERY 动作下<b>会遮蔽</b>的启用策略（{@code allowPlaintext != true}）
     * @param grantPolicies   该资源 EXPORT 动作下<b>允许明文</b>的启用策略
     * @return 缺口清单；空表示每条遮蔽都能在导出侧被合法放行
     */
    public static List<Gap> gaps(List<DataFieldPolicyEntity> maskingPolicies, List<DataFieldPolicyEntity> grantPolicies) {
        if (maskingPolicies == null || maskingPolicies.isEmpty()) {
            return List.of();
        }
        Map<String, Set<String>> grantedByRole = new LinkedHashMap<>();
        if (grantPolicies != null) {
            for (DataFieldPolicyEntity grant : grantPolicies) {
                if (!Boolean.TRUE.equals(grant.getAllowPlaintext())) {
                    // EXPORT 动作下的遮蔽策略不是"授权"，它只是把导出也遮上（当前实现不允许这样配导出）
                    continue;
                }
                grantedByRole.computeIfAbsent(normalizeRole(grant.getRoleCode()), role -> new LinkedHashSet<>())
                        .add(grant.getFieldName());
            }
        }

        List<Gap> gaps = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DataFieldPolicyEntity masking : maskingPolicies) {
            if (Boolean.TRUE.equals(masking.getAllowPlaintext())) {
                continue;
            }
            String field = masking.getFieldName();
            if (field == null || field.isBlank()) {
                continue;
            }
            String role = normalizeRole(masking.getRoleCode());
            if (!seen.add(role + '#' + field)) {
                continue;
            }
            if (covers(grantedByRole, role, field)) {
                continue;
            }
            gaps.add(new Gap(field, role, reasonFor(role)));
        }
        return gaps;
    }

    /**
     * 角色覆盖规则：该角色自己的 EXPORT 授权，或 {@code default} 的 EXPORT 授权
     */
    private static boolean covers(Map<String, Set<String>> grantedByRole, String role, String field) {
        Set<String> own = grantedByRole.get(role);
        if (own != null && own.contains(field)) {
            return true;
        }
        Set<String> fallback = grantedByRole.get(ColumnMaskingService.DEFAULT_ROLE_CODE);
        return fallback != null && fallback.contains(field);
    }

    private static String reasonFor(String role) {
        if (ColumnMaskingService.DEFAULT_ROLE_CODE.equals(role)) {
            return "兜底遮蔽（对所有角色生效）没有配套的兜底 EXPORT 明文授权："
                    + "任何没被单独授权的角色导出都会整份被拒";
        }
        return "该角色此字段被遮蔽，但没有 actionName=EXPORT 且允许明文的策略："
                + "导出是一整份文件，缺这一个字段就会整份被拒";
    }

    /**
     * {@code null} / 空 / {@code default} 都算"兜底"，与 {@code ColumnMaskingService} 的解析一致
     */
    private static String normalizeRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return ColumnMaskingService.DEFAULT_ROLE_CODE;
        }
        return ColumnMaskingService.DEFAULT_ROLE_CODE.equalsIgnoreCase(roleCode.trim())
                ? ColumnMaskingService.DEFAULT_ROLE_CODE
                : roleCode.trim();
    }
}
