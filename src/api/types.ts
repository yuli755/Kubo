/**
 * API 类型定义（兼容 shim）
 *
 * 类型已迁移至 @kubo/shared 包（packages/shared/src/types/api.ts），
 * 此处仅做再导出以兼容旧导入路径。新代码请直接 `import from '@kubo/shared'`。
 */

export type {

  ChangePasswordRequest,

  RefreshTokenRequest,
  RefreshTokenResponse,

  TenantOption,
  UserEntity,

} from '@kubo/shared'
