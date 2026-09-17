package cn.hamm.spms.base.spi;

import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;

/**
 * <h1>底座向业务域要的能力：行级写保护</h1>
 *
 * <p>与 {@link DataPermissionEnforcer} 是两层不同的事实：那边问"这个动作配了策略没有"，
 * 这边问"<b>这一行</b>归不归你管"。缺了后者，"只能改本仓单据"这类规则在写侧是空的
 * —— 请求方把主键改成别人的单据照样能存进去。</p>
 *
 * <p>本接口只负责<b>判定与拒绝</b>，不负责取数：实现需要既存行时由自己按
 * {@link BaseEntity#getId()} 去查（写侧的这一行必须是库里的既存行，而不是请求体里那份）。</p>
 *
 * @author Hamm.cn
 */
public interface RowPermissionEnforcer {
    /**
     * 校验这一行是否在当前主体的可写范围内
     *
     * @param entity      待写入实体（{@link BaseEntity#getId()} 为 {@code null} 表示新增）
     * @param resourceKey 资源标识（{@code data_resource.resource_key} 的口径）
     * @param action      写入动作
     */
    void enforce(BaseEntity<?> entity, String resourceKey, DataPermissionAction action);
}
