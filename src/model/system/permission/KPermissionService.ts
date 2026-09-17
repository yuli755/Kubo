import { AbstractCurdService } from '@/framework'
import { PermissionEntity } from './PermissionEntity'

/**
 * # 权限服务（新底座）
 *
 * 同 `KMenuService`：为角色的**权限授权弹窗**（批 3）提供 `getTreeList`，
 * 权限页本身的迁移留到 system 批。与旧 `PermissionService` 指向同一后端接口。
 */
export class KPermissionService extends AbstractCurdService<PermissionEntity> {
  entityClass = PermissionEntity
  baseUrl = 'permission'
}
