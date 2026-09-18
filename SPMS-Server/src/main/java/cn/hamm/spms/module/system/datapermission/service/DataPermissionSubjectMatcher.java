package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;

/**
 * <h1>策略主体可达性判定（"这条策略对当前人算不算数"，只此一份）</h1>
 *
 * <p>同一套 subjectType 规则此前被抄了三份：{@code DataPermissionService#matchesSubject}（管理端预览与
 * 可达性列表用）、{@code CachedPermissionRuleProvider#matchesSubject}（带缓存的运行时提供者）、
 * {@code TestPermissionRuleProvider}（测试替身）。抄写的代价不是重复代码，而是<b>会各自漂移</b>：
 * 三份对 {@code TENANT} / {@code USER} 的 null 处理就写得不一样（有的直接 {@code equals} 会 NPE），
 * 加一个维度（比如工厂）必然漏一处。收口后取<b>三份里最防御</b>的那份语义。</p>
 *
 * <p>语义要点（都不显眼，故写在这里）：</p>
 * <ul>
 *   <li>{@code null} / 未知 subjectType 一律 <b>false</b>（fail-closed：认不出主体就不放行政策）；</li>
 *   <li>{@code SYSTEM} 恒真（全局策略，与主体无关）；</li>
 *   <li>{@code DEPARTMENT} 走 {@link PermissionContext#coversDepartment(Long)}，
 *       用的是"含子部门的范围"而非直属集合 —— 判据本身也收在 context 上，
 *       免得这里与谓词构建处各判一次。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
public final class DataPermissionSubjectMatcher {

    private DataPermissionSubjectMatcher() {
    }

    /**
     * 判定策略主体是否与当前上下文匹配
     *
     * @param policy  策略；为 null 视为不匹配
     * @param context 权限上下文；为 null 视为不匹配
     * @return 匹配返回 true
     */
    public static boolean matches(DataPolicyEntity policy, PermissionContext context) {
        if (policy == null || context == null) {
            return false;
        }
        String subjectType = policy.getSubjectType();
        if (subjectType == null) {
            return false;
        }
        return switch (subjectType) {
            case "SYSTEM" -> true;
            case "USER" -> context.userId() != null && context.userId().equals(policy.getSubjectId());
            case "TENANT" -> context.tenantId() != null && context.tenantId().equals(policy.getSubjectId());
            case "ROLE" -> policy.getRoleCode() != null && context.roleCodes().contains(policy.getRoleCode());
            case "DEPARTMENT" -> context.coversDepartment(policy.getSubjectId());
            case "FACTORY" -> policy.getSubjectId() != null && context.factoryIds().contains(policy.getSubjectId());
            case "WAREHOUSE" -> policy.getSubjectId() != null && context.warehouseIds().contains(policy.getSubjectId());
            default -> false;
        };
    }
}
