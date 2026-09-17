// 批 3：角色的「菜单授权」弹窗需要菜单树，这里补一份新底座服务（系统域页面迁移时复用）。
export { KMenuService } from '@/model/system/menu/KMenuService'

// F3 数据层收敛（D5）：CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { MenuService } from '@/model/system/menu/MenuService'

/**
 * # System 菜单列表查询 Hook
 */

/**
 * # System 菜单树查询 Hook
 */

/**
 * # System 菜单详情查询 Hook
 */

/**
 * # System 菜单删除 Mutation Hook
 */
