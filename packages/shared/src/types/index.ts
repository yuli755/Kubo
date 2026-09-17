/**
 * 共享类型定义
 *
 * - tenant.ts：租户选项（`TenantOption`）
 * - mask.ts：列级脱敏策略类型（`MaskPolicy` / `MaskType`）
 *
 * 注：原先的 `api.ts`（`BaseEntity` / `PageRequest` / `PageResponse` /
 * `UserEntity` / `PermissionEntity` … 脚手架类型）已于 2026-09 删除 ——
 * 全仓无人 import，且 `PageResponse` 的 Spring Data 分页口径与后端实际返回
 * （`{ list, total }`）相冲突，留着只会误导。
 */

export * from './mask'
export * from './tenant'
