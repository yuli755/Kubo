import { AbstractCurdService } from '@/framework'
import { MenuEntity } from './MenuEntity'

/**
 * # 菜单服务（新底座）
 *
 * 菜单页本身还没迁（system 域，最后一批），但**角色的菜单授权弹窗**在批 3 就要用：
 * 它只需要 `getTreeList`（全量树，不分页）。为两个调用点提前把系统域的菜单页一起迁，
 * 会把批次的边界搞糊；而让 personnel 的弹窗回头依赖 airpower 服务，又与"该域零 airpower"的目标冲突。
 * 所以这里只补这一份薄服务（三行配置、零业务），系统域迁移时直接复用。
 *
 * 与旧 `MenuService` 指向同一后端接口（`baseUrl = 'menu'`）。
 */
export class KMenuService extends AbstractCurdService<MenuEntity> {
  entityClass = MenuEntity
  baseUrl = 'menu'
}
