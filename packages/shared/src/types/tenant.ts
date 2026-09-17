/**
 * 租户相关类型
 *
 * origin：`src/api/types.ts` 里的 `TenantOption`。原先同文件里的
 * `UserEntity` / `PermissionEntity` / `PageResponse`（Spring Data 风格的
 * `content` / `totalElements` 分页）等一批接口是脚手架遗留：既没有任何消费者，
 * 又与后端真实契约不一致（本仓分页是 `{ list, total }`），2026-09 清理时删除。
 */

/** 当前用户可切换的租户（`user/getMyTenantList` 返回项） */
export interface TenantOption {
  id: number
  code: string
  name: string
  isDefault: boolean
}
