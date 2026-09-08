package cn.hamm.spms.module.system.datapermission.service;

/**
 * <h1>权限变更 Outbox 投递状态机</h1>
 *
 * <p>与 {@code permission_change_outbox.status} 列的取值一一对应（字符串 = {@link #name()}）。</p>
 *
 * <ul>
 *   <li>{@code PENDING} → 待投递（写侧落库时的初始态）；</li>
 *   <li>{@code PROCESSING} → 派发中（Dispatcher 取出后、投递完成前）；</li>
 *   <li>{@code DONE} → 投递成功；</li>
 *   <li>{@code DEAD} → 重试次数耗尽，进死信（需人工重放）。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
public enum PermissionChangeOutboxStatus {
    PENDING,
    PROCESSING,
    DONE,
    DEAD
}
