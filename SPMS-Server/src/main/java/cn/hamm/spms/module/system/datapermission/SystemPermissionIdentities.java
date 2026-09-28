package cn.hamm.spms.module.system.datapermission;

import java.util.Set;

/**
 * <h1>系统级权限标识</h1>
 *
 * @author Hamm.cn
 */
public final class SystemPermissionIdentities {
    public static final String ROOT = "system:root";
    public static final String DATA_PERMISSION_BYPASS = "system:data-permission:bypass";

    /**
     * 归属迁移权限（B1）：允许管理员显式迁移一条带归属数据（{@code tenant_id}/{@code creator_id}）的归属。
     * 默认不授予任何角色 —— 归属列防篡改是默认「一律不可变」，只有显式持有该权限的角色
     * 才能走 {@code POST /dataResource/migrate-ownership} 这个专用接口，且每次迁移都落审计。
     */
    public static final String DATA_PERMISSION_OWNERSHIP = "system:data-permission:ownership";

    /**
     * 死信重放权限（P5）：允许管理员查看/重放/丢弃权限变更 Outbox 的死信事件（{@code DEAD}）。
     * 默认不授予任何角色 —— 死信是「连续重试仍投不出去」的异常态，处置是运维特权，
     * 每次重放/丢弃都落审计（见 {@code PermissionChangeOutboxAdminService}）。
     */
    public static final String DATA_PERMISSION_OUTBOX = "system:data-permission:outbox";

    /**
     * 上面四个治理特权身份的集合。
     *
     * <p>存在的理由是它们**不由 {@code PermissionScanner} 扫描产生** —— 扫描器遍历的是
     * Controller 的 HTTP 端点，而这四个身份是授予给角色的能力开关，没有对应端点。
     * 因此任何「用扫描结果作为应有权限全集」的逻辑都必须把它们并进去，否则会把它们
     * 判成过期项。{@code PermissionService.syncPermissionCatalog()} 就踩过这个坑：
     * 差集把它们算作 stale，一旦运维开启 {@code kubo.permission.purge-stale}，
     * 启动即删掉绕过/归属迁移/死信管理三项特权，非 root 角色从此永远拿不到，
     * 而且因为是启动期删的，界面上看不出任何操作痕迹。</p>
     *
     * <p>新增治理身份时只改这里，播种与同步两侧会同时跟上。</p>
     */
    public static final Set<String> ALL = Set.of(
            ROOT,
            DATA_PERMISSION_BYPASS,
            DATA_PERMISSION_OWNERSHIP,
            DATA_PERMISSION_OUTBOX
    );

    private SystemPermissionIdentities() {
    }
}
