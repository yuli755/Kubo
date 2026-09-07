package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionResourceStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <h1>行级策略「指向缺失 / 未启用资源」的静态盘点</h1>
 *
 * <p>与 {@link RowPolicyRiskAnalyzer}（策略自身写错）互补：这里盘的是策略的<em>资源引用</em>坏了。
 * 两条判据都锚在运行期那两行代码，不是推测：</p>
 * <ul>
 *   <li><b>孤儿策略</b>：运行期取策略的入口是 {@code DataPermissionService#getPolicies(resourceKey,…)}，
 *       它先用 {@code resourceKey} 反查 {@code data_resource} 行、再拿该行的 {@code id} 去
 *       {@code findByResourceIdAndActionName…} 取策略。一条 {@code resource_id} 在表里不存在的策略，
 *       <b>任何请求都轮不到它</b>（没有资源行的 id 等于它），但它仍躺在策略列表里显示「已启用」；</li>
 *   <li><b>指向未启用资源</b>：{@code DataPermissionService#findResource} 的判据是
 *       {@code resource == null || !"ENABLED".equals(resource.getStatus()) ? null : resource}。
 *       指向 {@code DRAFT / DISABLED} 资源的策略，运行期与孤儿一样取不到 —— 读侧退化成「未登记」
 *       （按 {@code deny-unregistered} 处理），写侧直接报「资源未注册或未启用」。</li>
 * </ul>
 *
 * <p><b>刻意不算风险的两件事</b>：{@code enabled=false} 的策略行（那是关着的，不是坏的）；
 * 引用的资源存在且 {@code ENABLED}（无论策略自身有没有其它问题，那归 {@link RowPolicyRiskAnalyzer} 管）。</p>
 *
 * <p>本类不查库、不判灰度模式，输入由调用方给（与 {@link FieldPolicyGapAnalyzer} / {@link RowPolicyRiskAnalyzer}
 * 同一套做法）。</p>
 *
 * @author Hamm.cn
 */
public final class PolicyResourceReferenceAnalyzer {

    /**
     * 策略引用的资源 ID 在 data_resource 里不存在（资源被删、策略未清理）
     */
    public static final String KIND_DANGLING_RESOURCE = "DANGLING_RESOURCE";

    /**
     * 策略引用的资源存在但状态不是 ENABLED（DRAFT / DISABLED）
     */
    public static final String KIND_RESOURCE_NOT_ENABLED = "RESOURCE_NOT_ENABLED";

    /**
     * 一条风险
     *
     * @param policyId   策略行主键
     * @param policyCode 策略编码（人认这个，不认 ID）
     * @param resourceId 策略引用的资源 ID（孤儿时为它引用的那个失效 ID；空引用时为 null）
     * @param kind       风险类别常量
     * @param detail     人话说明（直接给管理端渲染，别在前端重算语义）
     */
    public record Risk(Long policyId, String policyCode, Long resourceId, String kind, String detail) {
    }

    private PolicyResourceReferenceAnalyzer() {
    }

    /**
     * @param enabledPolicies 全部启用中的行级策略（跨资源）
     * @param resourcesById   全部已登记资源（含未启用），按 id 索引；null 安全
     * @return 风险清单；每条策略最多一条（孤儿优先于未启用，两者不会同时成立）
     */
    public static List<Risk> risks(List<DataPolicyEntity> enabledPolicies, Map<Long, DataResourceEntity> resourcesById) {
        if (enabledPolicies == null || enabledPolicies.isEmpty()) {
            return List.of();
        }
        List<Risk> risks = new ArrayList<>();
        for (DataPolicyEntity policy : enabledPolicies) {
            Long resourceId = policy.getResourceId();
            if (resourceId == null) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), null, KIND_DANGLING_RESOURCE,
                        "策略没有资源引用（resourceId 为空）：运行期按 资源 + 动作 精确取策略，这一行任何请求都不会读到。"
                                + "若它是该主体在此资源上的唯一策略，enforce 后会落到默认拒绝。请删除或补全该策略。"));
                continue;
            }
            DataResourceEntity resource = resourcesById == null ? null : resourcesById.get(resourceId);
            if (resource == null) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), resourceId, KIND_DANGLING_RESOURCE,
                        "策略引用的资源 ID=" + resourceId + " 在「数据资源」里不存在（资源已删除但策略未清理）"
                                + " ⇒ 运行期没有任何资源行会映射到这个 id，这一行永不生效。请删除该策略或补登记该资源。"));
                continue;
            }
            if (!DataPermissionResourceStatus.ENABLED.name().equals(resource.getStatus())) {
                risks.add(new Risk(policy.getId(), policy.getPolicyCode(), resourceId, KIND_RESOURCE_NOT_ENABLED,
                        "策略引用的资源 " + resource.getResourceKey() + " 状态为 " + resource.getStatus()
                                + "（非 ENABLED）⇒ 运行期 findResource 返回 null、按未登记处理，这一行永不生效。"
                                + "请到「数据资源」启用该资源后再谈放开。"));
            }
        }
        return risks;
    }
}
