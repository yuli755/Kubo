// 批 3：角色的「权限授权」弹窗需要权限树，这里补一份新底座服务（系统域页面迁移时复用）。
export { KPermissionService } from '@/model/system/permission/KPermissionService'

/**
 * # System 权限树数据查询 Hook
 *
 * 权限为树形实体（ITree），使用 `getTree` 获取全量树数据，无需分页。
 *
 * @example
 * ```ts
 * const { data, isFetching } = usePermissionTree()
 * ```
 */

/**
 * # System 权限详情查询 Hook
 *
 * @example
 * ```ts
 * const { data, isLoading } = usePermissionDetail(1)
 * ```
 */

/**
 * # 当前用户权限标识列表查询 Hook
 *
 * 从后端获取当前用户的权限标识列表（string[]），用于权限检查。
 * 配合 `usePermissionStore` 使用，加载后自动写入 store。
 *
 * @example
 * ```ts
 * const { data, isFetching } = useUserPermissionList()
 * ```
 */
