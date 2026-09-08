package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.TenantAwareEntity;

/**
 * <h1>归属列（{@code tenant_id} / {@code creator_id}）的写侧统一处置</h1>
 *
 * <p>补齐 §19.27 缺口③（「改归属列本身无人管」）里<b>可自动化、不需产品再拍板</b>的那一半：
 * 归属列由 D1 定为服务端推导、客户端不可信，于是「客户端能不能改归属」不该是开放问题 ——
 * 新建覆盖为服务端值（{@link TenantAwareEntity#fillAttribution}，{@code @PrePersist}），
 * 更新则恢复为库中既存值（本类）。</p>
 *
 * <p>为什么更新是「恢复库中值」而不是「覆盖为当前上下文」：{@code creator_id} 是<b>创建人</b>，
 * 任何一次别人编辑都不该把它改成当前操作者；{@code tenant_id} 是<b>数据所属租户</b>，
 * 行级过滤已保证用户只能触达自己租户的行，把它改成当前租户是等价的无操作、改成别的租户就是越权。
 * 所以两者在更新时一律不动，只有新建才由服务端推导。</p>
 *
 * <p>取数（回表读既存值）在 {@code BaseService#beforeUpdate}，判定（「归属 = 库中值」）在这里 ——
 * 与本模块其余守卫同一个理由：结论必须能脱离容器、不起 Spring、不连库地被逐条断言。</p>
 *
 * @author Hamm.cn
 */
public final class AttributionWriteProtection {

    private AttributionWriteProtection() {
    }

    /**
     * 更新时把归属列恢复为库中既存值
     *
     * <p>目标实体的归属列无论带着什么客户端提交值，一律覆盖为既存行的值。
     * 既存值为 {@code null} 时同样覆盖为 {@code null}（不猜、不兜底），
     * 兜底到「当前上下文」会把数据迁到错误的租户，兜底到 {@code 0} 则与 D1「不得回落 0L」冲突。</p>
     *
     * @param target   待写入实体（其归属列将被覆盖）
     * @param tenantId 库中既存租户 ID
     * @param creatorId 库中既存创建人 ID
     */
    public static void restoreOnUpdate(TenantAwareEntity<?> target, Long tenantId, Long creatorId) {
        target.setTenantId(tenantId);
        target.setCreatorId(creatorId);
    }
}
