package cn.hamm.spms.base.spi;

import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;

/**
 * <h1>底座向业务域要的能力：动作级数据权限校验</h1>
 *
 * <p>回答的问题是"这个主体<b>配了</b>该资源的这个动作没有"。它只看策略，不看数据行 ——
 * 行级判定是 {@link RowPermissionEnforcer} 的事。两者是两层不同的事实，缺任一层都会留洞：
 * 少了这一层，没配策略的资源谁都能写。</p>
 *
 * <p>{@link #enforce} 只允许有两种结局：放行，或抛业务异常拒绝。实现里<b>不许</b>出现
 * "配置读不到就放过"以外的静默分支 —— 判不了就说清楚为什么判不了。</p>
 *
 * @author Hamm.cn
 */
public interface DataPermissionEnforcer {
    /**
     * 校验当前主体对该资源的动作权限
     *
     * @param resourceKey 资源标识（{@code data_resource.resource_key} 的口径）
     * @param action      动作
     */
    void enforce(String resourceKey, DataPermissionAction action);
}
