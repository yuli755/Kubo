package cn.hamm.spms.module.system.datapermission;

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

    private SystemPermissionIdentities() {
    }
}
