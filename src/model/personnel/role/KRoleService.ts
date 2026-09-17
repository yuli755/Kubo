import type { MenuEntity } from '@/model/system/menu/MenuEntity'
import type { PermissionEntity } from '@/model/system/permission/PermissionEntity'
import { AbstractCurdService } from '@/framework'
import { RoleEntity } from './RoleEntity'

/**
 * # 角色服务（新底座）
 *
 * 两个授权动作（`authorizeMenu` / `authorizePermission`）不在 CRUD 基类里，
 * 用 `api()` 拼同一套请求管道：请求体是角色实体，`menuList`/`permissionList`
 * 只带 id（`copyOnlyId()`）—— 后端只认 id，带上整行数据会被当成"要改关联对象"。
 *
 * 与旧 `RoleService` 的差别只有基类；旧的那份在本批迁移后已无消费者，随之删除。
 */
export class KRoleService extends AbstractCurdService<RoleEntity> {
  entityClass = RoleEntity
  baseUrl = 'role'

  /** 为角色授权菜单 */
  async authorizeMenu(id: number, menuList: MenuEntity[]): Promise<void> {
    const role = new RoleEntity(id)
    role.menuList = menuList.map(item => item.copyOnlyId())
    await this.api('authorizeMenu').request(role)
  }

  /** 为角色授权权限 */
  async authorizePermission(id: number, permissionList: PermissionEntity[]): Promise<void> {
    const role = new RoleEntity(id)
    role.permissionList = permissionList.map(item => item.copyOnlyId())
    await this.api('authorizePermission').request(role)
  }
}
