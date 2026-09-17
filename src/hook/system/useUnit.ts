// 新底座服务（F2 试点页用）：同样是"视图只从 hook 取"的约定，避免视图层直接依赖 @/model
export { KUnitService } from '@/model/system/unit/KUnitService'
// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 计量单位的 CRUD 走 airpower 的 Service 类（useTanStackTable / useEditor / ASelector 需要类实例），
// 因此由本 hook 模块统一转发 —— 视图从这里取，不再回头依赖 @/model。
export { UnitService } from '@/model/system/unit/UnitService'

/**
 * # System 计量单位列表查询 Hook
 */

/**
 * # System 计量单位详情查询 Hook
 */

/**
 * # System 计量单位删除 Mutation Hook
 */
